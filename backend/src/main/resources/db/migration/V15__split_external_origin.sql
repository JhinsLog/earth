-- 외부 플레어의 주체(origin)를 둘로 나눈다(D-22).
--   PUBLIC   : 공공 정보. 지금 벌어지는 상황(서울시 교통 돌발). 채팅·사실 확인을 연다.
--   FESTIVAL : 축제·행사. 공식 공지를 옮긴 일정(스키장 개장, 파리 행사). 채팅·사실 확인을 닫는다.
-- 지금까지는 둘 다 API였고, 채팅을 닫을지는 "API이면서 LEISURE 분류"로 추측했다. 그 추측을 그대로 옮겨 나눈다 —
-- 지금 있는 외부 레저 플레어는 모두 스키장·파리 행사이고, 서울 돌발은 레저 분류를 쓰지 않는다.
UPDATE flares SET origin = 'FESTIVAL' WHERE origin = 'API' AND category = 'LEISURE';
UPDATE flares SET origin = 'PUBLIC' WHERE origin = 'API';

-- 엔티티 밖에서 엉뚱한 값이 들어오지 않게 막는다.
ALTER TABLE flares ADD CONSTRAINT ck_flares_origin CHECK (origin IN ('USER', 'PUBLIC', 'FESTIVAL'));
