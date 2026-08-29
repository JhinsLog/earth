# 금지 사항

> 로드 조건: **설정·인증·보안을 바꾸거나, 기존 코드를 지우거나 되돌리려 할 때.**
> 여기 있는 항목은 전부 과거에 실제로 문제가 났던 것이고, 대부분 해당 코드에
> 이유를 적은 한국어 주석이 붙어 있다. **지우기 전에 그 주석을 읽는다.**

## 1. 보안 불변 규칙

과거 커밋에서 결함을 재현·수정한 결과다. 되돌리는 변경은 하지 않는다.

- **시크릿에 폴백 기본값 금지.** `earth.jwt.secret`은 `${JWT_SECRET:}`로 비워둔다.
  공개 저장소에 적힌 키로 서명하면 누구나 임의 `userId`로 토큰을 위조할 수 있다.
  `JwtTokenProvider`가 기동 시점에 검증해 실패시킨다 — **잘못 뜨느니 안 뜨는 게 안전하다.**
- **DB 자격증명은 환경변수로 오버라이드 가능해야 한다.** 통로가 없으면 커밋된 파일에
  운영 비밀번호를 적게 된다.
- **`springdoc`(swagger-ui, api-docs)은 기본값 off**, `application-local.yml`에서만 켠다.
  엔드포인트 목록·파라미터·에러 코드는 공격자에게 그대로 지도가 된다.
- **`com.earth.dev`는 `@Profile("local")` 전용.** 인증 없이 임의 계정 토큰을 발급한다.
  `DevSecurityConfig`가 활성화 시 기동 로그에 경고를 크게 남긴다 — **이 경고를 지우지 않는다.**
  프로파일 하나에만 기대면 배포 설정에 잘못 들어갔을 때 아무도 눈치채지 못한다.
- **도배 제한은 서버에서 강제한다** (이벤트 5건/시간, 채팅 20건/분). 클라이언트 안내는 우회 가능하다.
- **소유권·사용자 스코프 검사를 새 엔드포인트에도 적용한다**
  (`SubscriptionService.delete`가 기준 예시).

## 2. 되돌리지 말 것 — 백엔드

각 항목의 상세한 이유는 해당 파일 주석에 있다. 요약된 증상만 적는다.

| 하지 말 것 | 되돌리면 나는 증상 | 파일 |
| --- | --- | --- |
| Redis 구독자를 `MessageListenerAdapter`로 감싸기 | 어댑터가 JSON 본문을 JDK 직렬화로 풀다 예외를 내고 **내부에서 삼켜** 메시지가 소리 없이 사라짐 | `config/RedisConfig.java` |
| Jackson 2 API(`GenericJackson2Json…`)로 되돌리기 | Spring Boot 4 / Spring Data Redis 4는 Jackson 3(`tools.jackson`) 기반 — 부팅 실패 | `config/RedisConfig.java` |
| `StompHeaderAccessor.wrap()`으로 `setUser()` | 헤더가 **복사**되어 Principal이 유실 → 연결은 되는데 메시지를 보내면 무반응 | `security/jwt/StompAuthChannelInterceptor.java` |
| Principal을 2-arg 생성자로 만들기 | `authenticated=false`가 되어 메시지 보안 적용 시 거부됨 | 〃 |
| `@MessageMapping` 파라미터의 `@Valid` 제거 | DTO 검증이 통째로 무시 → 공백 메시지 저장, 초과 길이는 DB 제약에 걸려 500 | `controller/ChatController.java` |
| `@MessageExceptionHandler` + `@SendToUser` 제거 | 메시지 처리 실패가 클라이언트에 전혀 전달되지 않음(전송 버튼 눌러도 무반응) | 〃 |
| `HttpStatusEntryPoint(UNAUTHORIZED)` 제거 | 미인증에 302 리다이렉트 → axios가 구글 로그인 HTML을 받고, 프론트 401 리프레시 인터셉터가 영영 미동작 | `config/SecurityConfig.java` |
| `spring-boot-flyway` 의존성 제거 | Spring Boot 4에서 Flyway 자동설정이 별도 모듈로 분리됨 — 마이그레이션 미실행 | `build.gradle.kts` |
| `spring.config.import: optional:file:.env…` 제거 | `.env`를 읽지 않아 모든 시크릿이 누락 | `application.yml` |

## 3. 되돌리지 말 것 — 프론트엔드

대부분 `components/globe/MapGlobe.tsx`에 있고, 각 지점에 이유가 주석으로 붙어 있다.

| 하지 말 것 | 되돌리면 나는 증상 |
| --- | --- |
| `dragRotate`/`pitch`를 다시 켜기 | 대기광(halo)이 "화면 중심 대칭 원"이라는 계산 전제가 깨져 어긋남 |
| halo를 MapLibre 네이티브 Sky API로 대체 | Sky는 pitch가 있어야 렌더링 — 수직 시점에서 아예 안 보임 |
| 국가 라벨을 스타일 `filter`의 `distance` 식으로 처리 | 이 버전은 filter 컨텍스트에서 `distance`를 지원하지 않아 **항상 false** |
| 자전 루프에서 `map.isEasing()` 검사 제거 | `setCenter`가 진행 중인 `flyTo`를 취소 → 별을 클릭해도 확대되지 않음 |
| 자전 재개를 `moveend` 대신 고정 타이머로 | 이동이 아직 진행 중일 때 자전이 끼어들어 확대가 중간에 멈춤 |
| 초기화 훅을 `render` 대신 `once('load')`로 | `load`는 한 번뿐이라 이미 지나갔으면 영영 미실행 → 초기 위치 이동 유실 |
| `EVENT_FOCUS_ZOOM`을 16보다 올리기 | Esri Dark Gray 타일 원본이 z16까지 — 확대가 아니라 화질 저하 |
| Blue Marble을 GIBS 원격 타일로 되돌리기 | GIBS가 `no-store`로 응답해 캐싱 불가 → 타일 경계가 사각형으로 드러남 |
| `attributionControl` 제거 | **라이선스 위반.** OpenStreetMap 파생 데이터(ODbL)는 출처 표기가 의무 |

- Blue Marble 타일 경로는 `/tiles/bluemarble/{z}/{y}/{x}.jpg` — **`{y}/{x}` 순서에 주의.**

## 4. 라이선스

저장소는 **All Rights Reserved**(포트폴리오/데모 목적 공개). 단 지도 이미지·데이터는 제3자
저작물로 각자의 라이선스를 따른다 — NASA GIBS(퍼블릭 도메인), OpenStreetMap/OpenFreeMap(ODbL),
Esri. `LICENSE`의 THIRD-PARTY ASSETS AND DATA 항목과 지도 화면의 attribution 표기를 함께 유지한다.
