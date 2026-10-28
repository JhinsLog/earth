-- 화면 범위 조회(EventRepository.findVisibleWithinRanges)와 전 세계 최신 조회를 받치는 인덱스.
--
-- 측정: 이력 100만(만료·삭제) + 보이는 별 20만(한국 70%), 커스텀 계획, 7회 중앙값.
--   쿼리(해당 행)        기존       이 파일 적용 후
--   전 세계 최신 500      45.2 ms    1.6 ms
--   한국 범위(14만)       67.3 ms    1.0~1.9 ms
--   서울 범위(641)        12.7 ms    1.7~3.1 ms
--   유럽 범위(95)          8.8 ms    0.4~0.9 ms
--
-- 두 인덱스를 함께 두는 이유: 범위의 밀도에 따라 맞는 길이 다르다. 빽빽한 범위는 최신순으로 걷다가
-- 500개에서 멈추는 쪽이, 성긴 범위는 위경도로 해당 행만 읽고 정렬하는 쪽이 빠르다. 플래너가
-- 통계를 보고 고른다(그래서 범위 조회는 매번 계획한다 — EventRepository 주석).
-- 둘 다 보이는 별만 담는다. 만료·삭제 이력은 계속 쌓이지만 조회 대상이 아니다.
--
-- GiST(PostGIS 점)도 비교했다. 한국 범위에서 플래너가 행 수를 잘못 추정해 GiST로 14만 행을 다
-- 읽었고(117 ms), 점 데이터의 사각형 검색에서는 B-tree보다 나은 점이 없었다.
CREATE INDEX idx_events_active_created ON events (created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_events_active_lat_lng ON events (latitude, longitude) WHERE status = 'ACTIVE';

-- 위 인덱스로 대체된다. lat_lng는 만료 이력까지 담아 측정 데이터에서 36 MB로 가장 컸고, 범용
-- 계획에서 한국 범위가 이력 84만 행을 읽게 만들었다(436 ms). status는 만료 스케줄러가 쓰는
-- 부분 인덱스 idx_events_expires_active(V2)와 겹친다. 둘 다 등록마다 쓰기 비용만 낸다.
DROP INDEX idx_events_lat_lng;
DROP INDEX idx_events_status;
