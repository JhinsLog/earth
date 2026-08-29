# CLAUDE.md

**Earth** — 3D 지구본 위에 실시간으로 이벤트(별)를 남기고, 그 자리에서 채팅하는 서비스.
`backend/`(Java 25 · Spring Boot 4.0.6) + `frontend/`(React 19 · Vite · MapLibre GL 5) 모노레포.
사람이 읽는 소개는 `README.md`, `backend/README.md`, `frontend/README.md`.

---

## 이 문서의 사용 규약 (토큰 규약)

**이 파일은 매 세션 컨텍스트에 자동으로 실린다.** 그래서 여기엔 *모든* 작업에 공통으로
필요한 것만 둔다. 영역별 상세는 아래 표를 보고 **그 작업을 할 때만** 읽는다.

- 상세 문서를 `@경로`로 참조하지 않는다 — `@`는 즉시 인라인되어 분리 효과가 사라진다.
  일반 경로로 적고, 필요한 시점에 Read 한다.
- 이 파일에 내용을 추가하기 전에 자문한다: **"모든 작업에 필요한가?"**
  아니라면 `.claude/docs/` 아래 상세 문서로 보낸다.
- 상세 문서의 내용을 여기에 요약해 중복시키지 않는다. 요약도 토큰이다.
  예외는 아래 "절대 금지" — 문서를 안 읽고 어길 위험이 토큰 비용보다 크다.

### 문서 라우팅

| 하려는 작업 | 읽을 파일 |
| --- | --- |
| 코드를 작성·수정한다 (백/프론트 무관) | `.claude/docs/conventions.md` |
| 계층·모듈 경계, 실시간 경로, DB 스키마를 건드린다 | `.claude/docs/architecture.md` |
| 설정·인증·보안을 바꾸거나, 기존 코드를 지우려 한다 | `.claude/docs/prohibited.md` |
| 커밋 메시지를 쓴다 | `.claude/docs/conventions.md` (§커밋) |

---

## 절대 금지

문서를 읽지 않았더라도 이것만은 지킨다. 어기면 조용히 보안 구멍이 나거나 운영이 깨진다.

1. **시크릿에 폴백 기본값을 두지 않는다.** `${JWT_SECRET:}` 처럼 비워둔다. 값이 없으면
   기동 실패가 정상 동작이다. `.env`(`backend/.env`, `frontend/.env`)는 커밋하지 않는다.
2. **`com.earth.dev` 패키지는 `@Profile("local")` 전용.** 인증 없이 임의 계정 토큰을
   발급하므로 운영 경로에 노출시키지 않는다.
3. **엔티티를 바꾸면 Flyway 마이그레이션을 새로 추가한다** (`V2__`, `V3__`…).
   `ddl-auto: validate`라 스키마가 어긋나면 기동이 실패한다. `V1__init.sql`은 수정 금지.
4. **실시간 브로드캐스트는 반드시 Redis pub/sub을 경유한다.** 서비스에서
   `SimpMessagingTemplate`을 직접 호출하지 않는다 (다중 인스턴스에서 한쪽에 고립된다).
5. **"왜 이렇게 했는지" 주석이 붙은 코드는 지우기 전에 그 주석을 읽는다.**
   불필요해 보이는 방어 코드 대부분은 오래 추적한 버그의 해결책이다. → `prohibited.md`

---

## 명령어

```bash
# 백엔드 (http://localhost:8080, local 프로파일 자동 활성화)
cd backend && docker compose up -d   # PostgreSQL:5432 + Redis:6379
cp .env.example .env                 # JWT_SECRET, GOOGLE_CLIENT_ID/SECRET 필수
./gradlew bootRun
./gradlew build                      # 변경 후 검증

# 프론트엔드 (http://localhost:5173)
cd frontend && npm install
npm run dev
npm run build                        # tsc -b + vite build (타입체크 포함, 변경 후 검증)
npm run lint                         # oxlint
```

**자동화 테스트와 CI가 없다.** `backend/src/test`도 프론트 테스트 설정도 `.github/`도 없다.
따라서 변경 후 검증은 위 빌드 명령 + 실시간 경로 수동 확인(이벤트 등록이 다른 브라우저에
즉시 뜨는지, 채팅 송수신)이 전부다. 두 계정이 필요한 시나리오는 local 전용
`GET /api/dev/login?nickname=테스터` 로 만든다.

---

## 파일 위치

탐색 라운드트립을 줄이기 위한 지도. 없는 것을 찾기 전에 여기부터 본다.

| 무엇 | 어디 |
| --- | --- |
| 보안 필터체인 / CORS | `backend/…/config/SecurityConfig.java` |
| STOMP 브로커·엔드포인트 | `backend/…/config/WebSocketConfig.java` |
| Redis 채널·직렬화 | `backend/…/config/RedisConfig.java` |
| JWT 발급·검증 | `backend/…/security/jwt/JwtTokenProvider.java` |
| 실시간 릴레이 | `backend/…/realtime/RedisMessage{Publisher,Subscriber}.java` |
| 설정값(`earth.*`) | `backend/src/main/resources/application.yml` + `config/*Properties.java` |
| DB 스키마 | `backend/src/main/resources/db/migration/` |
| 에러 코드 | `backend/…/exception/ErrorCode.java` |
| 지도 로직 전부 | `frontend/src/components/globe/MapGlobe.tsx` (598줄) |
| 지도 스타일·타일 소스 | `frontend/src/components/globe/mapStyle.ts` |
| 지도 수치 상수 | `frontend/src/components/globe/constants.ts` |
| HTTP 클라이언트(토큰 갱신) | `frontend/src/lib/api.ts` |
| STOMP 클라이언트 | `frontend/src/lib/ws.ts` |
| 백엔드 DTO 대응 타입 | `frontend/src/types.ts` |
