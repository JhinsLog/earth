package com.earth.security.oauth2;

import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        User user = upsertGoogleUser(
                oAuth2User.getAttribute("sub"),
                oAuth2User.getAttribute("email"),
                oAuth2User.getAttribute("name"),
                oAuth2User.getAttribute("picture"));

        return new EarthOAuth2User(user.getId(), oAuth2User.getAttributes());
    }

    /**
     * 구글이 준 프로필로 사용자를 만들거나 갱신한다.
     *
     * <p>이름과 사진 주소는 구글이 정하는 값이라 길이를 신뢰할 수 없다. 그대로 저장하면 컬럼
     * 길이를 넘는 순간 로그인 전체가 500으로 실패한다(2026-10-14, 사진 주소가 500자를 넘어
     * 신규 가입이 막혔다). 둘 다 부가 정보이므로 잘라내거나 비우고 로그인은 진행시킨다.
     */
    @Transactional
    User upsertGoogleUser(String googleId, String email, String name, String picture) {
        String nickname = fitNickname(name, email);
        String profileImageUrl = fitProfileImageUrl(picture, googleId);

        return userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, googleId)
                .map(existing -> {
                    existing.updateProfile(nickname, profileImageUrl);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        new User(AuthProvider.GOOGLE, googleId, email, nickname, profileImageUrl)));
    }

    // PostgreSQL VARCHAR(n)은 문자(코드 포인트) 수로 센다. String.length()로 자르면 이모지 같은
    // 서로게이트 쌍이 반으로 쪼개져 깨진 문자가 저장된다.
    private static String fitNickname(String name, String email) {
        String nickname = (name == null || name.isBlank()) ? email.substring(0, email.indexOf('@')) : name.strip();
        if (nickname.codePointCount(0, nickname.length()) <= User.NICKNAME_MAX_LENGTH) {
            return nickname;
        }
        return nickname.substring(0, nickname.offsetByCodePoints(0, User.NICKNAME_MAX_LENGTH));
    }

    // 주소는 잘라내면 깨진 링크가 되므로 비운다. 화면(Navbar)은 사진 없이 이름만 보여준다.
    // 주소 자체 대신 길이만 남긴다. 상한을 다시 정할 때 필요한 건 길이다.
    private static String fitProfileImageUrl(String picture, String googleId) {
        if (picture == null || picture.length() <= User.PROFILE_IMAGE_URL_MAX_LENGTH) {
            return picture;
        }
        log.warn("프로필 사진 주소가 {}자로 상한 {}자를 넘어 저장하지 않습니다. providerId={}",
                picture.length(), User.PROFILE_IMAGE_URL_MAX_LENGTH, googleId);
        return null;
    }
}
