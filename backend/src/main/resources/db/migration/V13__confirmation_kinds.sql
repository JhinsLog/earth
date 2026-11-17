-- 사실 확인을 세 가지로 나눈다(D-20).
--   SEEN     나도 봤어요   — 지금도 그 상황이 있다. 지금까지의 공감과 같고, 이것만 수명을 늘린다.
--   ENDED    끝났어요      — 상황이 정리됐다.
--   DISPUTED 사실과 달라요 — 잘못된 정보 같다.
--
-- 한 사람은 한 플레어에 셋 중 하나만 남긴다. 기존 (flare_id, user_id) 유니크 제약을 그대로 쓰고, 종류를 바꾸면
-- 같은 행의 kind만 바꾼다. 셋을 따로 누를 수 있게 하면 "봤어요"와 "끝났어요"를 동시에 누르는 모순이 생긴다.
-- 기존 행은 모두 "나도 봤어요"다.
ALTER TABLE flare_confirmations
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'SEEN'
        CHECK (kind IN ('SEEN', 'ENDED', 'DISPUTED'));

-- 종류별 수. confirm_count(= SEEN)처럼 목록 조회에서 플레어마다 COUNT를 돌리지 않게 비정규화한다(V3과 같은 이유).
ALTER TABLE flares ADD COLUMN ended_count INT NOT NULL DEFAULT 0;
ALTER TABLE flares ADD COLUMN disputed_count INT NOT NULL DEFAULT 0;
