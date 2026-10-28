-- 구글 프로필 사진 URL이 500자를 넘어 신규 가입이 500으로 실패했다.
--
-- picture 값은 구글이 발급하는 불투명한 문자열이라 길이를 우리가 정할 수 없다. 화면에 보이는
-- 사진이 같아도 주소 형식은 바뀔 수 있다. 실제로 2026-10-14부터 insert into users가
-- "value too long for type character varying(500)"로 세 번 실패했다.
--
-- 2048은 브라우저·CDN이 관례적으로 다루는 URL 길이 상한이다. 이것도 넘치면 애플리케이션이
-- 사진을 비우고 로그인을 진행한다(CustomOAuth2UserService). 컬럼만 넓히면 다음에 더 긴 값이
-- 왔을 때 같은 장애가 반복되므로, 두 장치가 함께 있어야 한다.
ALTER TABLE users
    ALTER COLUMN profile_image_url TYPE VARCHAR(2048);
