# 🌍 Earth

> 전 세계 사람들이 자신이 있는 지역에서 일어난 일을 3D 지구본 위에 실시간으로 공유하는 서비스.
> 실사 지구본을 드래그·줌으로 탐색하다가, 이벤트가 있는 곳을 클릭하면 별처럼 반짝이는 지점으로
> 확대되며 내용을 확인하고, 로그인 사용자는 그 자리에서 실시간 채팅에 참여할 수 있습니다.

백엔드와 프론트엔드가 공존하는 모노레포로 관리합니다.

---

## 🛠 기술 스택

### Backend (`backend/`)
- Java 25, Spring Boot 4.0.6
- PostgreSQL + Flyway 마이그레이션
- Redis — 실시간 이벤트/채팅 브로드캐스트(pub/sub) 및 refresh token 저장
- Spring Security + OAuth2 Client(Google) + JWT
- STOMP over WebSocket

### Frontend (`frontend/`)
- React 19 + TypeScript + Vite
- `maplibre-gl` — globe projection 3D 지구본
- Zustand, Axios, `@stomp/stompjs`

---

## 📡 아키텍처

이벤트 하나가 등록되면, 그 순간 접속해 있는 **모든 사용자의 화면에** 실시간으로 별이 반짝입니다.

```
클라이언트 → POST /api/events → 백엔드가 DB 저장 후 Redis에 publish
                                        ↓
                Redis pub/sub (여러 백엔드 인스턴스로 스케일아웃해도 동일하게 동작)
                                        ↓
              각 인스턴스가 STOMP로 접속 중인 모든 클라이언트에 /topic/events 브로드캐스트
```

이벤트별 채팅방(`/topic/chat.{eventId}`)도 동일한 Redis pub/sub 경로를 타므로, 서버를 여러 대로
늘려도 채팅 메시지가 한쪽 인스턴스에 고립되지 않습니다.

---

## ⚡ 개발하며 부딪힌 문제들

풀스택을 최신 버전(Spring Boot 4.0.x, MapLibre GL 최신)으로 구성하다 보니, 문서화가 아직
따라오지 못한 지점에서 실제로 막히는 문제들을 직접 원인 추적해서 해결했습니다.

- **Spring Boot 4.0의 모듈 재구성**: 4.0부터 Flyway 자동설정이 `spring-boot-flyway`라는 별도
  모듈로 분리되고, Redis 직렬화기(`GenericJackson2JsonRedisSerializer`)와 STOMP 리스너 메서드
  시그니처(`byte[]` → `String`)도 함께 바뀌어 있었습니다. 셋 다 런타임 부팅 실패로만 드러나서
  Spring Data Redis 소스코드까지 직접 확인해 원인을 특정했습니다.
- **globe 투영에서 정확한 대기광(halo) 그리기**: MapLibre의 네이티브 Sky API는 지형 pitch가
  있을 때만 렌더링되어 수직 시점에는 아예 표시되지 않는 것을 확인하고, 대신 현재 시점에서
  실제로 보이는 지구본 가장자리 각도를 `project()`/`unproject()` 왕복 검증으로 이진탐색해
  구한 뒤 그 반지름에 맞춰 CSS 글로우를 그리는 방식으로 구현했습니다(카메라가 가까워질수록
  보이는 가장자리 각도가 90°에서 계속 줄어드는 원근 효과까지 반영).
- **국가 라벨 데클러터링**: MapLibre의 `distance` 필터 표현식이 이 버전에서 filter 컨텍스트를
  지원하지 않아 항상 무효화되는 것을 소스 레벨에서 확인하고, 대신 `querySourceFeatures` +
  대권거리 계산으로 현재 시점 근처 국가만 라벨을 남기는 동적 필터를 직접 구현했습니다.

---

## 🚀 실행

```bash
# 백엔드
cd backend
docker compose up -d      # PostgreSQL + Redis
cp .env.example .env      # GOOGLE_CLIENT_ID/SECRET, JWT_SECRET 채우기
./gradlew bootRun

# 프론트엔드
cd frontend
cp .env.example .env
npm install
npm run dev
```

Google Cloud Console에서 OAuth 클라이언트를 발급받아 승인된 리디렉션 URI에
`http://localhost:8080/login/oauth2/code/google`을 등록해야 로그인이 동작합니다.

---

## 📂 디렉토리 구조

```text
earth/
├── backend/     # Spring Boot API 서버
│   ├── build.gradle.kts
│   └── src/
└── frontend/    # React + MapLibre GL 웹 클라이언트
    ├── package.json
    └── src/
```

각 서브프로젝트의 세부 구현 범위와 알려진 제약은 `backend/README.md`, `frontend/README.md`를
참고하세요.

---

## 🧭 다음 단계

- 레벨/EXP 시스템 상세 정책 확정 및 구현
- 지역 구독 알림 실시간 푸시(현재는 폴링)
- 이벤트 만료/자동 종료 스케줄러
- 자동화 테스트, CI/CD

---

## 📄 License

**All Rights Reserved.** 이 저장소는 포트폴리오/데모 목적으로 소스를 공개한 것으로,
무단 사용·복제·수정·배포·상업적 이용을 허용하지 않습니다. 자세한 내용은 [LICENSE](./LICENSE)를
참고하세요.

단, 지도 이미지·데이터는 제3자 저작물이며 각자의 라이선스를 따릅니다 — NASA GIBS(퍼블릭
도메인), OpenStreetMap/OpenFreeMap(ODbL), Esri, CARTO. 위 권리 유보는 여기에 미치지
않습니다. [LICENSE](./LICENSE)의 THIRD-PARTY ASSETS AND DATA 항목을 참고하세요.
