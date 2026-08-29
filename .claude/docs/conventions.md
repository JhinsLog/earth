# 코딩 컨벤션

> 로드 조건: **코드를 작성·수정할 때.** 커밋 메시지만 쓴다면 §커밋만 보면 된다.
> 전체 규약과 절대 금지 사항은 루트 `CLAUDE.md`에 있다.

## 공통

- **주석은 "무엇"이 아니라 "왜"를 쓴다.** 이 저장소 주석의 기본 어조는 한국어 서술체이고,
  대부분 "이렇게 하지 않으면 무슨 증상이 났는지"를 설명한다. 새 주석도 그 밀도를 따른다.
  코드를 읽으면 알 수 있는 것을 반복하는 주석은 쓰지 않는다.
- 사용자에게 보이는 문자열은 **한국어**. 예외는 지도 라벨 — `lib/language.ts`가 감지한
  브라우저 언어를 따라간다(OpenMapTiles `name:xx`).
- 줄바꿈은 `.gitattributes`가 강제한다. `gradlew`와 `*.sh`는 **반드시 LF**(CRLF면 셔뱅이
  깨진다), `*.bat`/`*.cmd`는 CRLF.

## 백엔드 (Java)

### 계층별 책임

| 계층 | 하는 일 | 하지 않는 일 |
| --- | --- | --- |
| Controller | 입력 검증(`@Valid`), 인증 주체 추출(`@AuthenticationPrincipal User`) | 비즈니스 판단, `ResponseEntity`로 상태 코드 직접 생성 |
| Service | 정책 검사(레벨·도배 제한·소유권), 트랜잭션 경계 | 요청/응답 형식 처리 |
| Domain | 엔티티 + Repository (도메인별 같은 패키지) | 외부 의존성 참조 |
| DTO | 계층 간 데이터 전달 | 엔티티를 그대로 노출 |

### 세부 규칙

- **트랜잭션**: 서비스 클래스에 `@Transactional(readOnly = true)`를 걸고, 쓰기 메서드에만
  `@Transactional`을 다시 붙인다.
- **엔티티**: `@NoArgsConstructor(access = PROTECTED)` + 의미 있는 생성자. 세터를 만들지 않고
  `updateProfile()`, `markRead()`처럼 의도가 드러나는 메서드를 둔다. `createdAt`은 `@PrePersist`.
- **DTO**: 전부 `record`. 응답 DTO는 `from(엔티티)` 정적 팩토리를 갖는다.
- **예외**: 새 실패 상황은 `ErrorCode`에 HTTP 상태 + 기본 메시지를 함께 추가하고
  `EarthApiException`으로 던진다. `GlobalExceptionHandler`가 응답으로 변환한다.
- **설정값**: `application.yml`의 `earth.*` 아래에 두고 `config/`에 `@ConfigurationProperties`
  **record**를 만든다(`@ConfigurationPropertiesScan`이 켜져 있어 별도 등록 불필요).

  > **`@DefaultValue`를 반드시 붙인다.** 설정이 누락되면 `int`가 0으로 떨어지는데,
  > `minLevelToJoin=0`이면 레벨 제한이 조용히 사라지고 `maxPerHour=0`이면 아무도 등록할 수
  > 없게 된다. `ChatProperties`/`EventProperties` 주석이 이 이유를 설명한다.

## 프론트엔드 (TypeScript / React)

- **HTTP는 항상 `lib/api.ts`의 `api` 인스턴스로.** 요청 인터셉터가 Bearer 토큰을 붙이고,
  401에 대해 refresh 재발급 후 1회 재시도한다(동시 요청은 하나의 refresh 프로미스 공유).
  `axios`를 직접 import하면 이 로직이 전부 빠진다.
- **WebSocket은 `lib/ws.ts`의 `subscribeTopic()` / `publish()`만.** STOMP 클라이언트는 모듈
  싱글턴이고, 토큰이 바뀌면 연결을 새로 맺는다(서버가 CONNECT 프레임의 토큰으로 세션 신원을
  한 번만 정하므로, 비로그인 연결을 재사용하면 메시지가 조용히 무시된다).
- **상태**: 서버 데이터·인증은 zustand 스토어(`store/`), 화면 전용 상태는 컴포넌트 로컬 state.
  토큰만 localStorage에 영속되고 사용자 정보는 진입 시 `/api/users/me`로 다시 받는다.
- **지도**: 스타일 정의는 `components/globe/mapStyle.ts`, 수치는 `constants.ts`에 모은다.
  컴포넌트 안에 매직 넘버를 흩뿌리지 않는다.
- **좌표 계산**: 대권거리·방위 이동은 `lib/geo.ts`를 쓴다(백엔드
  `RegionSubscription.haversineKm`과 같은 공식).
- 타입은 `types.ts`에서 백엔드 DTO와 1:1로 맞춘다. 백엔드 DTO를 바꾸면 여기도 같이 바꾼다.

## 커밋

**제목은 한국어**로 쓰되 형식은 Conventional Commits를 따른다: `타입(범위): 제목`.
**타입 키워드는 영어를 유지한다** — 기존 히스토리 전체가 영어 키워드라 섞이면
`git log --grep`으로 이력을 뽑을 때 걸러지지 않는다.

| 타입 | 의미 | 사용 예시 |
| --- | --- | --- |
| `feat` | 기능 추가 — 사용자가 체감하는 새 동작 | `feat(globe): 임시 별 등록 플로우와 접속 지역 기반 초기 화면` |
| `fix` | 결함 수정 — 버그·보안 결함을 고침 | `fix(auth): 미인증 API 요청에 302 대신 401을 응답` |
| `perf` | 성능 개선 — 동작은 그대로, 속도·자원만 | `perf(globe): NASA Blue Marble 타일을 자체 호스팅으로 전환` |
| `docs` | 문서만 변경 — README·주석·라이선스 | `docs(license): 제3자 지도 저작물 표기 정리 및 앱 내 출처 표시 활성화` |
| `chore` | 잡무 — 위 어디에도 안 드는 정리 | `chore: 멀티 머신 개발 환경 설정 정리` |
| `refactor` | 리팩터링 — 겉보기 동작 없이 내부 구조만 | `refactor(chat): 도배 제한 검사를 ChatService에서 분리` |
| `build` | 빌드 설정 — 의존성·Gradle·Vite | `build(backend): Flyway를 spring-boot-flyway 모듈로 전환` |
| `test` | 테스트 추가·수정 | `test(event): 시간당 등록 제한 경계값 검증 추가` |

> 위 다섯(`feat`·`fix`·`perf`·`docs`·`chore`)은 실제 히스토리에서 인용했다.
> 아래 셋(`refactor`·`build`·`test`)은 아직 쓰인 적이 없어 **형식 참고용 예시**다.

**범위(scope)** 는 변경이 닿는 영역이다. 지금까지 쓰인 것: `globe`, `chat`, `auth`,
`security`, `dev`, `backend`, `license`. 저장소 전역에 걸치면 범위를 생략한다(`chore:`).
새 범위를 임의로 만들기 전에 위 목록에 맞는 것이 없는지 먼저 본다.

- 보안 결함 수정은 범위를 `security`로 모은다(`fix(security):`) — 나중에 보안 이력만
  따로 뽑아보기 위함이다.
- **제목만 쓰지 않는다.** 본문에 "무엇을 고쳤는지"보다 **"왜 그것이 문제였는지"와
  "어떻게 검증했는지"**를 쓴다. 재현 방법, 실패했던 응답 코드, 수정 후 확인 결과까지 남긴다.
  `git log`의 기존 커밋이 그대로 예시다.
- 커밋 금지: `backend/.env`, `frontend/.env`, `.claude/settings.local.json`, 빌드 산출물.
  `.claude/launch.json`은 공유 설정이므로 커밋한다.
