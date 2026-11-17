-- 경험치 정산(D-21). 사용자 플레어가 끝나면(만료·삭제) 사실 확인 수로 작성자의 경험치를 한 번 정산한다.

-- 정산한 시각. NULL이면 아직이다. 정산은 이 값을 플레어 행 잠금 안에서 확인·기록해 한 번만 일어난다.
ALTER TABLE flares ADD COLUMN settled_at TIMESTAMPTZ;

-- 이미 끝난 플레어는 정산하지 않는다. 규칙이 생기기 전의 활동에 소급해 경험치를 주지 않는다.
UPDATE flares SET settled_at = now() WHERE status <> 'ACTIVE' OR expires_at <= now();

-- 정산 대상 찾기("사용자 플레어 중 아직 정산하지 않은 것")에만 쓰는 부분 인덱스. 정산이 끝난 행은 들어가지 않아 작게 유지된다.
CREATE INDEX idx_flares_unsettled ON flares (expires_at) WHERE settled_at IS NULL AND origin = 'USER';

-- 경험치 원장. 경험치가 바뀐(또는 0으로 정산된) 일을 하나도 빠짐없이 남긴다. 분석·문의 대응의 근거다.
-- 외래 키를 걸지 않는다: 기록은 원본(사용자·플레어)이 정리돼도 남아야 하고, 쓰기 경로에 참조 검사를 더하지 않는다.
-- 개인 정보는 넣지 않는다(사용자 ID와 수치만).
CREATE TABLE exp_ledger
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    flare_id       BIGINT,
    reason         VARCHAR(30)  NOT NULL,
    -- 규칙이 계산한 변화량. 경험치는 0 아래로 내려가지 않으므로 실제 변화는 exp_after - exp_before다.
    delta          INT          NOT NULL,
    exp_before     INT          NOT NULL,
    exp_after      INT          NOT NULL,
    -- 정산에 쓴 사실 확인 수. 규칙이 바뀌어도 당시 판단을 다시 따져 볼 수 있게 남긴다.
    seen_count     INT          NOT NULL DEFAULT 0,
    ended_count    INT          NOT NULL DEFAULT 0,
    disputed_count INT          NOT NULL DEFAULT 0,
    -- 정산 당시 규칙(설정값 요약). 규칙을 바꾼 뒤에도 어떤 기준으로 계산했는지 남는다.
    rule           VARCHAR(200) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 한 플레어는 한 번만 정산한다. settled_at 확인이 1차 방어선이고, 이 제약이 마지막 방어선이다.
CREATE UNIQUE INDEX uq_exp_ledger_flare_reason ON exp_ledger (flare_id, reason) WHERE flare_id IS NOT NULL;
CREATE INDEX idx_exp_ledger_user ON exp_ledger (user_id, created_at);

-- 사실 확인 이력. flare_confirmations는 "지금 상태"만 담아(취소하면 행이 지워지고, 바꾸면 덮인다) 누가 언제 무엇을 눌렀다
-- 바꿨는지가 남지 않는다. 경험치의 근거가 되는 행동이라 덧붙이기만 하는 이력을 따로 둔다. 외래 키를 걸지 않는 이유는 위와 같다.
CREATE TABLE flare_confirmation_events
(
    id         BIGSERIAL PRIMARY KEY,
    flare_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    -- ADD(처음 누름), CHANGE(종류 바꿈), WITHDRAW(취소)
    action     VARCHAR(10) NOT NULL CHECK (action IN ('ADD', 'CHANGE', 'WITHDRAW')),
    from_kind  VARCHAR(10),
    to_kind    VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_confirmation_events_flare ON flare_confirmation_events (flare_id, created_at);
CREATE INDEX idx_confirmation_events_user ON flare_confirmation_events (user_id, created_at);
