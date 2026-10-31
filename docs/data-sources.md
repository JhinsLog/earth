# 외부 데이터 출처

Earth가 쓰는 외부 데이터와 서비스를 정리한다. 각 항목마다 무엇을 어디서 받는지, 언제 받는지,
라이선스, 실패하면 어떻게 되는지를 적는다. 새 출처를 붙이거나 바꿀 때 이 문서를 같이 고친다.

받는 시점은 세 가지다.

| 시점 | 뜻 | 예 |
| --- | --- | --- |
| 빌드 시점(정적) | 미리 받아 저장소에 넣었다. 실행 중에는 외부에 나가지 않는다 | 지구 텍스처, 국경 |
| 실행 중 · 서버 | 백엔드가 받아 가공해 둔다. 브라우저는 우리 서버만 본다 | 실시간 구름 |
| 실행 중 · 브라우저 | 브라우저가 외부에 직접 요청한다 | 지도 타일, IP 위치 |

## 한눈에 보기

| 데이터 | 출처 | 시점 | 갱신 | 라이선스 | 실패하면 |
| --- | --- | --- | --- | --- | --- |
| 홈 지구 낮 텍스처 | NASA GIBS, Blue Marble Next Generation | 빌드 | 고정 | NASA 공개 자료, 출처 표기 | — |
| 홈 지구 밤 불빛(설정에서 켤 때만) | NASA GIBS, VIIRS Black Marble | 빌드 | 고정 | NASA 공개 자료 | — |
| 홈 지구 정적 구름 | NASA Visible Earth, Blue Marble cloud composite | 빌드 | 고정 | NASA 공개 자료 | — |
| 홈 지구 육지/바다 마스크 | NASA GIBS `OSM_Land_Water_Map` (OpenStreetMap 기반) | 빌드 | 고정 | ODbL, © OpenStreetMap contributors | — |
| **홈 지구 실시간 구름(본체)** | **NASA GIBS WMS**(GOES-East·West, Himawari) + **EUMETSAT EUMETView WMS**(Meteosat 0°·인도양) | **서버** | **10분**(인도양 15분) | NASA 공개 자료 / EUMETSAT Core, CC-BY-4.0(표기 필요) | 위성별 이전 영상, 빈 지역은 GMGSI |
| 홈 지구 실시간 구름(바탕) | NOAA GMGSI (AWS 공개 버킷) | 서버 | 1시간 | 미국 정부 공개 자료 + Meteosat 부분은 EUMETSAT CC-BY-4.0(표기 필요) | 정적 구름으로 대체 |
| 지도 저배율 위성 타일 | NASA GIBS, Blue Marble | 빌드 | 고정 | NASA 공개 자료 | — |
| 지도 고배율 위성 타일 | Esri World Imagery | 브라우저 | Esri 측 | Esri 이용약관, 출처 표기 | 해당 줌에서 타일 없음 |
| 지도 어두운 배경 | Esri World Dark Gray Base | 브라우저 | Esri 측 | Esri 이용약관 | 배경 없음 |
| 지도 지명·경계 벡터 | OpenFreeMap (OpenMapTiles 스키마) | 브라우저 | OpenFreeMap 측 | ODbL, © OpenStreetMap contributors | 지명 없음 |
| 국가 경계(별의 국가 판정) | Natural Earth 1:10m Admin 0, 한국 관점(KOR) | 빌드(DB 마이그레이션 V7) | 고정 | 퍼블릭 도메인 | — |
| 접속 국가 | Cloudflare `CF-IPCountry` 요청 헤더 | 요청마다 | — | Cloudflare 기능 | 국가 모름 → 비로그인은 상세 없음 |
| 첫 화면 위치(IP 기반) | ipwho.is, geojs.io | 브라우저 | 요청마다 | 각 서비스 약관 | 기본 위치로 시작 |
| 로그인 프로필 | Google OAuth2 | 로그인 시 | 로그인마다 | Google API 약관 | 로그인 실패 |

## 실시간 구름: 정지궤도 위성 다섯 기 합성

지구를 한 바퀴 두르는 정지궤도 기상위성 다섯 기의 적외선(약 10.4 µm) 영상을 10분마다 받아 한 장으로 합친다.
원본 수치 파일이 아니라 각 기관이 웹 지도 서비스(WMS)로 공개하는 단일 채널 영상을 쓴다. 서버가 위경도 격자로
재투영해 주고, 라이선스도 이쪽이 자유롭다(아래).

| 위성 | 직하점 경도 | 받는 곳 | 레이어 | 간격 |
| --- | --- | --- | --- | --- |
| GOES-West | 137.2°W | NASA GIBS WMS | `GOES-West_ABI_Band13_Clean_Infrared` | 10분 |
| GOES-East | 75.2°W | NASA GIBS WMS | `GOES-East_ABI_Band13_Clean_Infrared` | 10분 |
| Meteosat-12 (MTG) | 0° | EUMETSAT EUMETView WMS | `mtg_fd:ir105_hrfi` | 10분 |
| Meteosat IODC | 45.5°E | EUMETSAT EUMETView WMS | `msg_iodc:ir108` | 15분 |
| Himawari | 140.7°E | NASA GIBS WMS | `Himawari_AHI_Band13_Clean_Infrared` | 10분 |

```
https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi           영상(GetMap, EPSG:4326, 2048×1024)
https://gibs.earthdata.nasa.gov/wmts/epsg4326/best/1.0.0/{레이어}/default/2km/all/{시작}--{끝}.xml   관측 시각 목록(DescribeDomains)
https://gibs.earthdata.nasa.gov/colormaps/v1.3/Clean_Longwave_Infrared_Window_Band.xml          색 → 섭씨 표
https://view.eumetsat.int/geoserver/ows                              영상(GetMap)
```

**처리 흐름** (`CloudMapRefreshScheduler`, 10분마다)

```
GeoRingFrames.refresh: 위성마다 이미 가진 것보다 새 영상이 있으면 받는다
  GIBS      → 시각 목록에서 최신 시각 → 영상(빈 영상이면 최대 3번 다시)
  EUMETView → 지금부터 관측 간격씩 거슬러 64×32로 있는지만 묻고, 있으면 전체 크기로
  GIBS 색 → 가장 가까운 표 색의 섭씨 → 3×3 중앙값(점 잡음 제거)
  3시간 넘은 영상은 버림
CloudComposer.compose:
  EUMETView 밝기 → 섭씨: 겹치는 GIBS 영상과 최소제곱 직선(실패하면 기본값)
  칸마다 위성별 가중치 max(cos 천정각 - 0.15, 0)^3 으로 섭씨 평균 → 구름 밀도
  어느 위성도 못 본 칸은 GMGSI로 채움
→ JPEG → CloudMapStore → GET /api/globe/clouds (X-Clouds-Observed-At = 쓰인 영상 중 가장 오래된 관측 시각,
  X-Clouds-Sources = 제공 기관)
프론트: 10분마다 다시 받고, 관측 시각이 바뀌었을 때만 텍스처를 갈아 끼운다
```

**측정** (2026-10-31 04:49 UTC, LattePanda Alpha)

| 항목 | 값 |
| --- | --- |
| 첫 수집(다섯 장) | 14.7초 |
| 새 영상이 없을 때 확인 | 4.4초 |
| 합성 | 0.5~0.7초 |
| 결과 | 2048×1024 JPEG 364KB |
| 받는 양 | 새 영상마다 GIBS 약 1.3MB, EUMETView 0.3~0.7MB. 10분에 약 5MB |
| 영상 나이(측정 시점) | 19~39분. GMGSI만 쓸 때는 약 35분~1시간 45분 |
| EUMETView 환산 | Meteosat-12: 온도 = -0.367 × 밝기 + 27.6 (겹침 3만 칸, RMS 6.6°C) / IODC: -0.249 × 밝기 + 22.7 (1.5만 칸, RMS 7.0°C) |

**막혔던 점과 판단**
- GIBS 영상의 색이 색상표와 정확히 일치하는 픽셀이 60%대였다. 원본 해상도 타일도 같았다(축소본에서 이웃
  색이 섞인다). 가장 가까운 색으로 되돌리고 3×3 중앙값으로 점 잡음을 걷어냈다. 이 필터 하나로 EUMETView
  환산 오차도 15°C → 6.5°C로 줄었다. 오차 대부분이 GIBS 쪽 잡음이었다.
- GIBS는 같은 주소를 여러 번 물으면 빈 영상과 정상 영상이 섞여 왔다(GOES-West, 세 번 중 1~2번 정상).
  작은 크기로 있는지만 묻는 방식은 거의 늘 빈 영상이라 쓸 수 없어서, 시각 목록(DescribeDomains)으로 최신 시각을
  정하고 빈 영상이면 다시 묻는다.
- EUMETView는 없는 시각을 물으면 HTTP 200에 XML 예외를 주고, 있는 시각이면 실제로 쓴 시각을 `Warning` 헤더에
  준다("99 Nearest value used: time=...").
- 위성마다 관측 시각이 최대 20분 다르다. 구름은 시속 30~60km로 움직여 20분이면 10~20km이고, 이미지 한 칸(약
  20km) 안이라 위성마다 최신 영상을 그대로 쓴다.
- 위성 원판 가장자리(천정각 81° 밖)는 쓰지 않는다. 비스듬히 보면 해상도가 떨어지고 구름이 두꺼워 보인다.

**라이선스**
- GIBS: NASA가 공개하는 영상. 출처 표기를 요청한다.
- EUMETView: EUMETSAT 데이터 정책(2026-01판) 4조에서 SEVIRI·FCI의 Advanced Image Products를 Core로 분류하고,
  정의에서 이를 "Web Map Services 등에 쓰이는 단일 채널 시각화, 원본 수치 미포함"이라 한다. Core는 5조에 따라
  CC-BY-4.0으로 누구나 무료로 재배포할 수 있고, 6.3조의 표기("Contains modified EUMETSAT Meteosat data 연도")가
  필요하다. 같은 Meteosat이라도 1시간 미만 지연의 원본 수치 파일은 유료(14조)라 쓰지 않는다.
- EUMETView 자체의 이용 약관·호출 한도 문서는 찾지 못했다. 10분에 영상 두 장 정도만 받는다.

## 실시간 구름 바탕: NOAA GMGSI

**무엇** — Global Mosaic of Geostationary Satellite Imagery. NOAA가 정지궤도 기상위성 다섯 기
(GOES-East·West, Himawari, Meteosat 두 기)의 장파 적외선(약 10.3~10.8 µm 대역) 영상을 이어 붙인
전 지구 합성 영상이다. 적외선이라 밤에도 구름이 보인다.

**어디서** — AWS의 NOAA 공개 데이터 버킷. 인증과 키가 없다.

```
https://noaa-gmgsi-pds.s3.amazonaws.com/?list-type=2&prefix=GMGSI_LW/yyyy/MM/dd/HH/   목록(S3 ListObjectsV2)
https://noaa-gmgsi-pds.s3.amazonaws.com/GMGSI_LW/yyyy/MM/dd/HH/GLOBCOMPLIR_..._s{관측 시작}_e..._c....nc
```

**형식** (2026-10-28 파일로 확인)

| 항목 | 값 |
| --- | --- |
| 파일 | NetCDF4(HDF5), 약 7MB, gzip 압축 청크 |
| 격자 | `data[1][3000][4999]` float32. 0~255로 정규화한 밝기온도(차가울수록 큼) |
| 투영 | **메르카토르.** 행 간격이 위도로는 0.072°~0.021°로 변하고 메르카토르 좌표로는 일정하다 |
| 범위 | 위도 약 ±72.7°, 경도 360°(0열이 -180°) |
| 갱신 | 매시 정각 관측분이 약 35분 뒤에 올라온다 |

**처리 흐름**

```
CloudMapRefreshScheduler (10분마다)
  → GmgsiClient.latestKey: 지금·1시간 전·2시간 전 폴더 목록에서 최신 키
  → 이미 처리한 키면 끝 (실제 다운로드는 시간당 1회)
  → GmgsiClient.download: 임시 파일로 받기 (40MB 상한, 키 모양 검증)
  → GmgsiFiles.renderJpeg: 256행씩 읽어 메르카토르 → 등장방형 재투영, 밝기 → 구름 밀도, 2048×1024 흑백 JPEG
  → CloudMapStore (메모리)
GET /api/globe/clouds → JPEG + X-Clouds-Observed-At 헤더 (없으면 404)
홈 지구본 → 받으면 위도 60°까지 실시간 구름, 70° 너머는 정적 구름. 404·실패면 정적 구름만
```

**측정** — 변환 2.1초(LattePanda Alpha, 3회 중 2~3회째), 결과 383KB. 다운로드는 시간당 약 7MB.

**구름 판정** — 구름 꼭대기는 차갑고 지표는 따뜻하다는 것만으로 가른다. 고위도는 지표 자체가
차가워 구름처럼 보이므로 문턱을 위도에 따라 올린다(적도 112 → 위도 70° 이상 150, 0~255 기준).
실제 파일을 지구본에 씌워 보며 정했다. 겨울 고원·극지 얼음처럼 차가운 지표는 옅은 구름으로 보일 수 있다.

**한계와 판단**
- 1시간 단위다. 실시간 영상(10분 단위)을 원하면 GIBS의 GOES·Himawari 레이어를 쓸 수 있지만,
  GIBS에는 유럽·아프리카·인도양을 덮는 Meteosat 레이어가 없어 그 지역이 빈다(아래 후보 표).
- 서버가 받아 가공한다. 브라우저가 직접 받게 하면 사용자마다 7MB를 받아야 하고, NetCDF를 브라우저에서
  풀어야 하며, 외부 장애가 그대로 사용자에게 보인다.
- 이미지는 메모리에만 둔다. 재시작하면 기동 직후 수집이 다시 채우고, 그 사이에는 정적 구름이 보인다.
- 주소에 확장자를 붙이지 않는다. `.jpg`면 Cloudflare가 엣지에 캐시하는데, CORS 헤더 없이 캐시된 응답이
  다른 출처 요청에 나가면 브라우저가 텍스처를 거부한다.
- 라이선스: NOAA가 NODD(Open Data Dissemination)로 공개 배포하는 미국 정부 자료다. 합성에 포함된
  Meteosat 자료는 EUMETSAT 데이터 정책(2026-01판)을 따른다. 정각 기준의 시간 단위 Level 1 자료와
  Web Map Service용 단일 채널 시각화(Advanced Image Product)는 Core로 분류되어 CC-BY-4.0으로 재배포가
  허용되고, 6.3조에 따라 "[Contains modified] EUMETSAT Meteosat data [연도]" 표기가 필요하다. 그래서 실시간
  구름일 때 출처 표기에 이 문구를 붙인다. 1시간 미만 지연의 비정각 Level 1 원본 수치 자료는 Recommended로,
  유료 라이선스(최종 사용자 연 4,000유로부터)이며 원본 수치의 재배포가 금지된다(4·5·6·14조).

**의존성** — HDF5 파일을 순수 자바로 읽으려고 `io.jhdf:jhdf:0.13.0`(MIT)을 쓴다. 네이티브 라이브러리가
필요 없다. 이 파일의 루트 속성에는 원본 위성 파일 목록이 14KB 문자열로 들어 있어 jhdf가 루트 속성을
읽다 실패한다. 그래서 위도 범위를 전역 속성 대신 `lat` 배열의 첫 값과 끝 값에서 읽는다.

## 낮과 밤

외부 데이터가 아니다. 태양이 머리 위에 있는 지점(태양 직하점)을 현재 시각만으로 계산한다(`frontend/src/lib/sun.ts`).

- **공식**: 미국 해군천문대 천문연감(Astronomical Almanac)의 저정밀 태양 위치식. 1950~2050년에 오차 약 0.01°다.
  NOAA의 일반 태양 위치식도 검토했는데, 2026~2027년을 7시간 간격으로 비교하니 위도가 최대 0.44°, 경도가 0.2° 어긋나 쓰지 않았다.
- **확인**: 2026-06-21 08:24 UTC(하지)에 위도 23.44°, 2026-03-20 14:46 UTC(춘분)에 위도 0.00°.
- **자전**: 실제와 같이 24시간에 한 바퀴다. 직하점을 매 프레임 구하고 정면이 그만큼 따라 돌게 해서, 화면에서 태양 방향은
  고정되고 지구가 그 아래에서 돌며 낮·밤 경계가 지표 위를 시간당 15°씩 지나간다. 자전각을 경과 시간의 누적이 아니라
  현재 시각에서 바로 구해, 탭이 백그라운드였다 돌아와도 태양과 어긋나지 않는다. 사용자가 끌어 돌리면 보는 위치만 바뀐다.
- **도시 불빛**: 밤 지역의 불빛(VIIRS Black Marble)은 별(이벤트)과 겹쳐 구분이 어려워 기본은 끈다.
  화면 오른쪽 위 설정에서 켤 수 있다. 이 설정은 브라우저(localStorage)에만 저장된다.

## 검토했지만 쓰지 않은 구름 출처

| 출처 | 내용 | 갱신 | 장점 | 쓰지 않은 이유 |
| --- | --- | --- | --- | --- |
| 위성 원본 파일 (AWS `noaa-goes19`·`noaa-goes18` ABI-L2-CMIPF, `noaa-himawari9` AHI-L1b) | 정확한 밝기온도 | 10분, 관측 10~13분 뒤 | 가장 빠르고 정확 | 10분에 약 72MB. Himawari는 전용 바이너리(HSD) 해석기가 필요. Meteosat 원본은 1시간 미만 지연이 유료 |
| EUMETView 전 지구 합성(`mumi:worldcloudmap_ir108`) | 적외선 전 지구 한 장 | 3시간 | 합성할 필요 없음 | 너무 드물다 |
| NASA GIBS 극궤도 레이어 (MODIS, VIIRS TrueColor) | 하루 한 번 컬러 사진 | 1일 | 가장 실사 같은 색 | 궤도 사이에 빈 띠가 생기고, 지역마다 촬영 시각이 다르다 |
| NOAA GFS | 예보 모델의 구름량 격자 | 6시간 | 전 지구, 예보까지 | 관측이 아니라 모델 계산값. GRIB2 처리가 무겁다 |
| 상용 날씨 API (OpenWeatherMap 구름 타일 등) | 구름 타일 | 수십 분 | 붙이기 쉽다 | API 키·호출 한도·약관. 웹 메르카토르 타일이라 구 텍스처로 다시 합쳐야 한다 |

## 브라우저가 직접 부르는 외부 서비스

브라우저가 직접 요청하면 사용자의 IP가 그 서비스에 전달된다.

- **Esri 타일**: 지도를 확대할 때마다. 출처 표기는 지도 오른쪽 아래(MapLibre)에 있다.
- **OpenFreeMap**: 지명·경계 벡터 타일과 글꼴.
- **ipwho.is, geojs.io**: 첫 화면을 사용자 근처로 돌리려고 IP로 대략의 위치를 묻는다. 두 곳에 물어 지역을 더
  구체적으로 답한 쪽을 쓰고, 제공자마다 4초 안에 답이 없으면 끊는다. 둘 다 실패하면 기본 위치로 시작한다. 비로그인 공개 범위를 정하는
  접속 국가는 이 값이 아니라 서버가 받는 Cloudflare 헤더로 판정한다(클라이언트가 국가를 주장하게 두면
  그대로 우회된다).
