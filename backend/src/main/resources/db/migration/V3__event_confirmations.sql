-- 공감(목격 확인). 같은 사건을 본 사람이 별의 신뢰도를 올린다.
--
-- 이 서비스의 전제는 "사건을 직접 보거나 겪은 사람이 별을 등록한다"이다. 공감은 그 전제를
-- 다른 목격자가 뒷받침하는 장치이며, 쌓일수록 별이 오래 남고 지구본에서 더 밝게 보인다.
-- 아무도 공감하지 않은 별은 원래 수명(30분)이 지나면 조용히 사라진다.
CREATE TABLE event_confirmations
(
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 사람이 같은 별에 여러 번 공감해 수명을 무한히 늘리지 못하게 한다.
    UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_confirmations_event ON event_confirmations (event_id);

-- 조회할 때마다 COUNT(*)를 돌리지 않도록 비정규화해 둔다. 지구본은 별을 최대 500개까지
-- 한 번에 그리므로 목록 조회에서 별당 집계 쿼리가 나가면 안 된다.
ALTER TABLE events ADD COLUMN confirm_count INT NOT NULL DEFAULT 0;
