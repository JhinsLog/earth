-- 구글에서 받던 개인정보를 지운다. 이제 구글에는 openid만 요청해 계정 ID(sub)만 받는다(docs/decisions.md D-16).
--
-- 이메일·사진 주소는 화면에 쓰이지 않거나(이메일) 내 상단 바에만 쓰였고(사진), 구글 계정 이름은 닉네임이 되어
-- 채팅·플레어에서 다른 사람에게 그대로 보였다(대개 실명). 셋 다 지우고, 구글 가입자의 닉네임은 자동 닉네임으로 바꾼다.
-- 계정·플레어·채팅·레벨은 그대로 둔다. 운영 DB의 구글 가입자 2명이 대상이며 삭제는 사용자가 확인했다(2026-11-15).

ALTER TABLE users
    DROP COLUMN email,
    DROP COLUMN profile_image_url;

-- 형식은 Nicknames.random()과 같다(지구인- + 6자). 여기서는 md5의 16진수 6자라 글자 범위만 좁다.
-- dev: 계정은 local 프로파일 전용 개발 로그인(DevAuthController)이 만든 것이라 이름이 실명이 아니다.
UPDATE users
SET nickname = '지구인-' || upper(substr(md5(random()::text || id::text), 1, 6))
WHERE provider = 'GOOGLE'
  AND provider_id NOT LIKE 'dev:%';

-- 닉네임은 하나뿐이다. 대소문자만 다른 닉네임("Earth"와 "earth")도 같은 것으로 본다 — 사칭을 막기 위해서다.
CREATE UNIQUE INDEX users_nickname_lower_key ON users (lower(nickname));
