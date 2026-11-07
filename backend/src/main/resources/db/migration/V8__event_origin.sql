-- 별을 누가 만들었는지. 사용자가 직접 남긴 별(USER)과 공공데이터를 받아 만든 별(API)을 상세 화면에서 구분한다.
-- 기존 별은 전부 사용자가 만든 것이다.
ALTER TABLE events ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 외부 데이터로 만든 별의 출처(예: SEOUL_TOPIS)와 그 출처에서의 고유 ID(서울시 돌발 acc_id).
-- 사용자 별은 둘 다 NULL이다.
ALTER TABLE events ADD COLUMN source VARCHAR(30);
ALTER TABLE events ADD COLUMN external_id VARCHAR(64);

-- 같은 출처의 같은 사건은 별 하나뿐이다. 수집은 스케줄러 하나가 돌지만, 백엔드를 여러 대로 늘리거나 수집이
-- 겹쳐 돌아도 두 번째 INSERT가 여기서 막힌다. 애플리케이션의 "있으면 갱신, 없으면 생성" 판단은 조회와 저장 사이에
-- 틈이 있어 그것만으로는 중복을 막지 못한다. 사용자 별(NULL, NULL)은 NULL끼리 같지 않아 제약에 걸리지 않는다.
-- 이 인덱스는 동기화가 출처별로 기존 별을 찾을 때도 쓴다.
CREATE UNIQUE INDEX uq_events_source_external_id ON events (source, external_id);
