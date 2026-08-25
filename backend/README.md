# Earth Backend

'Earth' — 전 세계 실시간 이벤트 공유 서비스의 백엔드입니다.

- Java 25 / Spring Boot 4.0.6
- PostgreSQL (Flyway 마이그레이션), Redis (실시간 브로드캐스트 pub/sub + refresh token 저장소)
- Google OAuth2 로그인 → JWT(access/refresh) 발급
- STOMP over WebSocket: `/topic/events`(전체 공개 실시간 이벤트 피드), `/topic/chat.{eventId}`(이벤트별 채팅방)

## 실행

```bash
docker compose up -d      # PostgreSQL + Redis
cp .env.example .env      # 값 채운 뒤
./gradlew bootRun
```

Google Cloud Console에서 OAuth 클라이언트를 만들고 승인된 리디렉션 URI에
`http://localhost:8080/login/oauth2/code/google` 를 등록해야 합니다.

## 구현 범위 (MVP)

- **인증**: `/oauth2/authorization/google` 로 로그인 시작 → 성공 시 `OAUTH_REDIRECT_URL`로
  `accessToken`/`refreshToken` 쿼리파라미터와 함께 리다이렉트. `/api/auth/refresh`로 재발급(회전),
  `/api/auth/logout`으로 폐기.
- **이벤트**: `GET /api/events`(bounding box 옵션) / `GET /api/events/{id}` 는 비로그인도 조회 가능
  (지구본에 별처럼 표시되어야 하므로). `POST /api/events` 는 로그인 필요.
  생성 즉시 Redis pub/sub → 모든 서버 인스턴스의 `/topic/events` 구독자에게 실시간 브로드캐스트.
- **지역 구독**: `POST/GET/DELETE /api/subscriptions` — 사용자가 관심 지역(중심좌표+반경)을 등록하면,
  그 반경 안에서 이벤트가 생성될 때 `notifications` 테이블에 알림이 쌓입니다(폴링용 `GET /api/notifications`).
- **이벤트 채팅방**: `GET/POST(WS) /api/events/{id}/chat` — 로그인 사용자만 접근 가능하고,
  `earth.chat.min-level-to-join` 설정값 이상 레벨만 메시지 전송 가능(기본값 1 = 로그인만 되어 있으면 가능).
  레벨/EXP 상세 정책은 확정되면 `User.level`을 갱신하는 서비스만 추가하면 됩니다.

## 다음 단계 (미구현)

- 레벨업 시 EXP 지급 트리거(출석/이벤트 생성/댓글 등) — 정책 미정
- 구독 알림의 실시간 푸시(현재는 폴링). STOMP user destination + 로그인 세션 매핑 추가 필요
- 이벤트 만료/자동 종료 스케줄러
- Testcontainers 기반 통합 테스트
