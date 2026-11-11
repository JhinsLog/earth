-- 사용자 차단. 차단한 사람(blocker)에게만 차단된 사람(blocked)의 플레어와 채팅이 보이지 않는다.
--
-- 차단은 보는 사람의 설정이다. 차단된 사람의 글을 지우거나 다른 사람에게서 숨기지 않는다.
-- 목록을 서버에 두는 이유는 기기를 바꿔도 유지되게 하기 위해서다. 거르는 일은 화면이 한다(docs/decisions.md D-13).
CREATE TABLE user_blocks
(
    blocker_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 같은 사람을 두 번 차단해도 한 행. 동시 요청은 INSERT ... ON CONFLICT DO NOTHING이 이 제약으로 정리한다.
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
