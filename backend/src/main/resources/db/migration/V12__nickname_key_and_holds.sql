-- 닉네임 사칭 방지를 강화한다(docs/decisions.md D-17).
--
-- V11은 대소문자만 무시하고 같은 이름을 막았다. 그래서 '별헤는밤'·'별헤는 밤'·'별헤는_밤', 'Illya'·'llya',
-- 'B0B'·'BOB'처럼 눈으로는 구별되지 않는 이름이 서로 다른 사람에게 갈 수 있었다. 비교용 키를 따로 두고
-- 그 키에 유니크를 건다.

-- 비교용 키. Nicknames.key()와 같은 규칙이다: 소문자로 바꾸고, 공백·밑줄·하이픈을 지우고,
-- 생김새가 같은 글자를 하나로 모은다(l·1 → i, 0 → o). 한쪽을 고치면 다른 쪽도 맞춘다.
ALTER TABLE users
    ADD COLUMN nickname_key        VARCHAR(50),
    -- 사용자가 마지막으로 닉네임을 바꾼 시각. 자동 닉네임만 쓰는 동안은 비어 있다. 변경은 30일에 한 번.
    ADD COLUMN nickname_changed_at TIMESTAMPTZ;

UPDATE users SET nickname_key = translate(lower(nickname), 'l10 _-', 'iio');

ALTER TABLE users ALTER COLUMN nickname_key SET NOT NULL;
DROP INDEX users_nickname_lower_key;
ALTER TABLE users ADD CONSTRAINT users_nickname_key_key UNIQUE (nickname_key);

-- 사용자가 버린 닉네임을 30일 동안 다른 사람이 가져가지 못하게 잡아 둔다. 버리자마자 남이 가져가면
-- 대화 상대는 그 사람을 원래 주인으로 착각한다. 원래 주인은 기간 안에도 되찾을 수 있다.
-- 같은 키를 다시 버리면 행을 덮어쓴다(키당 한 행).
CREATE TABLE nickname_holds
(
    nickname_key VARCHAR(50) PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    released_at  TIMESTAMPTZ NOT NULL
);
