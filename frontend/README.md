# Earth Frontend

'Earth' — 전 세계 실시간 이벤트 공유 서비스의 웹 프론트엔드 (MVP).

- React 19 + TypeScript + Vite
- `maplibre-gl` — globe projection 기반 3D 지구본. 저줌은 NASA Blue Marble 실사 이미지,
  줌인하면 Esri 위성 → CARTO Dark Matter로 크로스페이드, OpenFreeMap 벡터 타일로 접속 언어에
  맞춘 국경선/지명 라벨, 실측 기반 CSS 대기광(halo), 1시간에 1바퀴 자동 자전
- `zustand` — 인증/이벤트 상태
- `@stomp/stompjs` + `sockjs-client` — 실시간 이벤트 피드 & 이벤트별 채팅방

## 실행

```bash
cp .env.example .env   # 백엔드 주소 확인
npm install
npm run dev
```

백엔드(`../backend`)가 `http://localhost:8080`에서 떠 있어야 로그인/이벤트 등록/채팅이 동작합니다.

## 구조

- `components/globe` — MapLibre GL 지도(`MapGlobe`), 스타일 정의(`mapStyle`), 대기광(`GlobeHalo`),
  배경 별(`Starfield`), 이벤트 마커용 성광 텍스처(`starFlareTexture`)
- `components/panel` — 이벤트 상세 패널, 채팅방, 이벤트 등록 모달
- `components/layout` — 상단 내비게이션(로그인/등록 버튼)
- `store` — 인증 상태(zustand, localStorage 영속), 이벤트 목록 + 실시간 구독
- `lib` — axios(리프레시 토큰 인터셉터), STOMP 클라이언트, 접속 위치 감지(GPS/IP),
  대권거리·구면좌표 계산, 브라우저 언어 감지

## 지도 타일 소스 (전부 무료, API 키 불필요)

- NASA GIBS `BlueMarble_NextGeneration` — 최저 줌 실사 이미지
- Esri `World_Imagery` — 위성 사진
- CARTO `dark_nolabels` — 확대 시 전환되는 다크 배경(자체 라벨 없음, 벡터 라벨과 중복 방지)
- OpenFreeMap(`tiles.openfreemap.org`) — 국경선/지명 벡터 타일, 다국어 라벨

## 알려진 제약 (다음 단계)

- 지역 구독 알림은 현재 폴링(`GET /api/notifications`)만 지원하며 실시간 푸시는 아직 없습니다.
- 레벨/EXP UI는 표시만 하고, 상세 정책이 정해지면 그에 맞춰 레벨업 트리거를 추가해야 합니다.
- 국가 라벨은 지도 중심에서 일정 거리(km) 이내만 표시하도록 필터링되어 있습니다(시야 분산 방지).
