# 아키텍처 규칙

> 로드 조건: **계층·모듈 경계, 실시간 경로, 인증 흐름, DB 스키마를 건드릴 때.**
> 계층별 코딩 규칙은 `conventions.md`, 금지 사항은 `prohibited.md`.

## 불변식 1 — 실시간은 Redis pub/sub을 경유한다

```
클라이언트 → POST /api/events → EventService (DB 저장)
                                     ↓ RedisMessagePublisher
                   Redis pub/sub  earth:events / earth:chat:{eventId}
                                     ↓ RedisMessageSubscriber (모든 인스턴스)
              SimpMessagingTemplate → /topic/events, /topic/chat.{eventId}
```

백엔드 인스턴스를 여러 대로 늘려도 메시지가 한쪽에 고립되지 않게 하려는 설계다.
**새 실시간 기능을 추가할 때 서비스에서 `SimpMessagingTemplate`을 직접 호출하지 않는다.**
`RedisMessagePublisher`에 발행 메서드를 추가하고 `RedisMessageSubscriber`에 릴레이 분기를 넣는다.

### STOMP 목적지 계약

| 목적지 | 용도 | 인증 |
| --- | --- | --- |
| `/ws` (SockJS) | 연결 엔드포인트 | 불필요 |
| `/app/chat.{eventId}.send` | 클라이언트 → 서버 발행 | 필요 |
| `/topic/events` | 전 지구 신규 이벤트 피드 | 불필요(구독 가능) |
| `/topic/chat.{eventId}` | 이벤트별 채팅방 | 필요 |
| `/user/queue/errors` | 처리 실패 사유를 보낸 사람에게만 회신 | 필요 |

### Redis 키·채널

| 키/채널 | 용도 |
| --- | --- |
| `earth:events` | 신규 이벤트 브로드캐스트 채널 |
| `earth:chat:{eventId}` | 채팅 브로드캐스트 채널(패턴 구독) |
| `earth:refresh-token:{userId}` | 사용자당 refresh token 1개(회전·폐기용) |
| `earth:chat-rate:{userId}` | 채팅 도배 제한 카운터(1분 고정 윈도우) |

## 불변식 2 — 스키마는 Flyway가 소유한다

`spring.jpa.hibernate.ddl-auto: validate`다. 엔티티를 바꾸면 **반드시** 마이그레이션을 새로
추가한다(`V2__...sql`). 기존 `V1__init.sql`은 수정하지 않는다 — 이미 적용된 환경에서
체크섬 불일치로 기동이 실패한다.

테이블: `users`, `events`, `region_subscriptions`, `notifications`, `chat_messages`.

## 불변식 3 — 인증은 JWT, 세션은 없다

```
GET /oauth2/authorization/google
  → CustomOAuth2UserService (사용자 upsert)
  → OAuth2SuccessHandler (access/refresh 발급, refresh는 Redis 저장)
  → 302 → OAUTH_REDIRECT_URL?accessToken=..&refreshToken=..
  → 프론트 OAuthRedirectPage → localStorage(earth:accessToken / earth:refreshToken)
```

- 필터체인은 `STATELESS`. 서버 세션에 상태를 두지 않는다.
- JWT에 **`typ` 클레임**(`access` / `refresh`)이 있고, 진입점 세 곳에서 각각 용도를 검증한다:
  `JwtAuthenticationFilter`(HTTP), `StompAuthChannelInterceptor`(STOMP), `AuthController`(재발급).
  **새 인증 진입점을 추가하면 여기서도 반드시 용도를 검증한다.** 검증을 빠뜨리면 14일짜리
  refresh token이 API 인증에 그대로 통용된다.
- refresh token은 사용자당 하나만 Redis에 유지되어 재발급 시 회전되고 로그아웃 시 폐기된다.

## 엔드포인트 접근 정책

| 범위 | 대상 |
| --- | --- |
| 비로그인 허용 | `GET /api/events`, `GET /api/events/{id}`(지구본에 별이 보여야 하므로), `/api/auth/refresh`, `/ws/**`, `/oauth2/**`, `/login/**`, `/actuator/health` |
| 인증 필요 | 그 외 `/api/**`. 채팅 `/api/events/*/chat/**`은 조회·전송 모두 |

새 엔드포인트를 추가하면 `SecurityConfig`의 규칙에도 명시한다. `/api/**`가 기본 인증이므로
공개해야 하는 것만 예외로 올린다(그 반대가 아니다).

## 정책 수치 (전부 `earth.*` 설정값)

| 항목 | 기본값 | 위치 |
| --- | --- | --- |
| 이벤트 등록 제한 | 5건/시간 | `earth.event.max-per-hour` |
| 채팅 전송 제한 | 20건/분 | `earth.chat.max-per-minute` |
| 채팅 입장 최소 레벨 | 1 | `earth.chat.min-level-to-join` |
| access token 유효기간 | 1시간 | `earth.jwt.access-token-validity-ms` |
| refresh token 유효기간 | 14일 | `earth.jwt.refresh-token-validity-ms` |

**제한은 항상 서비스 계층에서 강제한다.** 클라이언트 안내는 우회 가능하다.

## 알려진 구조적 제약

새 기능을 붙이기 전에 이미 알려진 항목인지 확인한다.

- **레벨/EXP**: 스키마(`users.level`, `users.exp`)와 UI 표시는 있으나 EXP 지급 트리거가 없다.
  정책이 정해지면 `User.level`을 갱신하는 서비스만 추가하면 된다.
- **구독 알림**: 실시간 푸시가 없고 `GET /api/notifications` 폴링만 지원한다. 또
  `NotificationService.notifySubscribers()`가 `findAll()`로 전수 조회 후 메모리 필터링을 한다 —
  구독 수가 늘면 공간 인덱스 기반 질의로 바꿔야 한다.
- **이벤트 만료/자동 종료 스케줄러 없음** — `EventStatus.CLOSED`로 바꾸는 주체가 아직 없다.
- **프론트엔드에 알림·지역구독 UI가 없다** — API만 존재하고 화면에 연결되어 있지 않다.
