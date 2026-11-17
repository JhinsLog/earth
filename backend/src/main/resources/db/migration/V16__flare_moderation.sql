-- 사용자 플레어 신고와 임시 비공개(D-24).
-- 범죄 등 사건의 정확한 위치처럼 사람이 특정될 수 있는 플레어를 사용자가 신고하고, 신고가 쌓이면 운영자가 보기 전까지 가린다.

-- 아직 처리되지 않은 신고 수(운영자가 복구하면 0으로 돌아간다). 신고 행을 매번 세지 않도록 비정규화한다(confirm_count와 같은 이유).
ALTER TABLE flares ADD COLUMN report_count INT NOT NULL DEFAULT 0;
-- 지금 임시 비공개된 시각. 운영자 검토 목록의 순서이자 "언제부터 가려졌나"다. 복구하면 NULL.
ALTER TABLE flares ADD COLUMN hidden_at TIMESTAMPTZ;
-- 검토 목록 조회용. 가려진 플레어는 적다.
CREATE INDEX idx_flares_hidden ON flares (hidden_at) WHERE status = 'HIDDEN';

-- 신고. 한 사람은 한 플레어에 한 번만(유니크). 같은 사람이 여러 번 눌러 혼자 가리는 것을 막는다.
CREATE TABLE flare_reports
(
    id          BIGSERIAL PRIMARY KEY,
    flare_id    BIGINT      NOT NULL REFERENCES flares (id),
    reporter_id BIGINT      NOT NULL REFERENCES users (id),
    reason      VARCHAR(30) NOT NULL CHECK (reason IN ('LOCATION_EXPOSURE', 'PERSONAL_INFO', 'ABUSE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_flare_reports_flare_reporter UNIQUE (flare_id, reporter_id)
);
CREATE INDEX idx_flare_reports_reporter ON flare_reports (reporter_id, created_at);

-- 검열 조치 이력. 자동 가림·운영자 가림·복구·삭제를 하나도 빠짐없이 남긴다(누가, 왜, 그때 신고가 몇 건이었나).
-- exp_ledger처럼 외래 키를 걸지 않는다: 기록은 원본이 정리돼도 남아야 한다. actor_id가 NULL이면 시스템(신고 누적)이다.
CREATE TABLE moderation_actions
(
    id           BIGSERIAL PRIMARY KEY,
    flare_id     BIGINT       NOT NULL,
    actor_id     BIGINT,
    action       VARCHAR(20)  NOT NULL CHECK (action IN ('AUTO_HIDE', 'HIDE', 'RESTORE', 'REMOVE')),
    note         VARCHAR(200),
    report_count INT          NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_moderation_actions_flare ON moderation_actions (flare_id, created_at);
