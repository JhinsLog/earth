package com.earth.security.oauth2;

import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구글이 준 프로필 값이 컬럼 길이를 넘어도 로그인(사용자 저장)이 실패하지 않는지 검증한다.
 *
 * <p>2026-10-14 구글 사진 주소가 500자를 넘어 {@code insert into users}가
 * {@code value too long for type character varying(500)}로 실패했고, 신규 가입이 500으로 막혔다.
 * 실제 PostgreSQL에 저장해야 길이 제약이 재현되므로 통합 테스트로 둔다.
 */
class GoogleUserUpsertTest extends IntegrationTest {

    @Autowired
    private CustomOAuth2UserService service;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("500자를 넘는 사진 주소로도 신규 가입된다 — 장애 당시 재현값")
    void signUpWithUrlOver500() {
        String url = longUrl(600);

        User user = service.upsertGoogleUser("g-600", "a@example.com", "지구인", url);

        assertThat(reload(user).getProfileImageUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("상한(2048자)을 넘는 사진 주소는 비우고 가입은 진행한다")
    void signUpWithUrlOverLimit() {
        User user = service.upsertGoogleUser("g-3000", "b@example.com", "지구인", longUrl(3000));

        assertThat(reload(user).getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("기존 사용자의 사진 주소가 상한을 넘게 바뀌어도 로그인은 성공한다")
    void existingUserWithUrlOverLimit() {
        User first = service.upsertGoogleUser("g-existing", "c@example.com", "지구인", longUrl(100));

        User again = service.upsertGoogleUser("g-existing", "c@example.com", "지구인", longUrl(3000));

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(reload(again).getProfileImageUrl()).isNull();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("50자를 넘는 이름은 문자 단위로 잘리고, 이모지가 반으로 쪼개지지 않는다")
    void longNicknameIsTruncatedByCodePoint() {
        // "가" 49개 뒤에 이모지(서로게이트 쌍) — String.length()로 자르면 50번째에서 쪼개진다.
        String name = "가".repeat(49) + "🌍" + "나".repeat(10);

        User user = service.upsertGoogleUser("g-name", "d@example.com", name, null);

        String saved = reload(user).getNickname();
        assertThat(saved.codePointCount(0, saved.length())).isEqualTo(User.NICKNAME_MAX_LENGTH);
        assertThat(saved).endsWith("🌍");
    }

    @Test
    @DisplayName("이름이 없으면 이메일 앞부분을 닉네임으로 쓴다")
    void blankNameFallsBackToEmailLocalPart() {
        User user = service.upsertGoogleUser("g-noname", "earthling@example.com", null, null);

        assertThat(reload(user).getNickname()).isEqualTo("earthling");
    }

    private User reload(User user) {
        return userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, user.getProviderId()).orElseThrow();
    }

    private static String longUrl(int length) {
        String prefix = "https://lh3.googleusercontent.com/a-/";
        return prefix + "x".repeat(length - prefix.length());
    }
}
