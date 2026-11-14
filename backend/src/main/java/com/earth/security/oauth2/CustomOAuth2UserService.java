package com.earth.security.oauth2;

import com.earth.domain.user.User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 구글 프로필을 받아 Earth 사용자로 연결한다.
 *
 * <p>{@link DefaultOAuth2UserService}를 상속하지 않고 필드로 갖는다. 상속한 클래스에 {@code @Transactional}을
 * 걸면 스프링이 CGLIB 프록시를 만들고, 부모의 final 메서드를 가로챌 수 없다는 경고가 기동마다 찍혔다. 이 클래스에는
 * 트랜잭션을 걸지 않는다 — 구글 HTTP 호출 동안 DB 커넥션을 잡지 않도록 저장은 {@link GoogleUserRegistrar}가 맡는다.
 */
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> googleUserInfo = new DefaultOAuth2UserService();
    private final GoogleUserRegistrar registrar;

    public CustomOAuth2UserService(GoogleUserRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = googleUserInfo.loadUser(userRequest);

        User user = registrar.upsert(
                oAuth2User.getAttribute("sub"),
                oAuth2User.getAttribute("email"),
                oAuth2User.getAttribute("name"),
                oAuth2User.getAttribute("picture"));

        return new EarthOAuth2User(user.getId(), oAuth2User.getAttributes());
    }
}
