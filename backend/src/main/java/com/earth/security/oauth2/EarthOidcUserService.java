package com.earth.security.oauth2;

import com.earth.domain.user.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * 구글 로그인 결과(ID 토큰)를 Earth 사용자로 연결한다.
 *
 * <p>구글에는 {@code openid}만 요청하므로 ID 토큰에는 계정 ID(sub)와 발급 정보만 들어 있고, 필요한 것은 그 sub뿐이다.
 * 스프링 시큐리티 7의 {@link OidcUserService}는 요청 범위와 상관없이 userinfo 주소가 있으면 userinfo API를 부른다
 * (OidcUserRequestUtils.shouldRetrieveUserInfo, 7.0.5 바이트코드로 확인). 받을 것이 없는 호출이므로 끈다. 그래서 이
 * 단계에는 외부 호출이 없고, ID 토큰은 그 앞 단계에서 구글 공개키로 이미 검증됐다.
 *
 * <p>트랜잭션을 걸지 않는다. 저장은 {@link GoogleUserRegistrar}가 맡는다. 이 클래스에 트랜잭션을 걸면 CGLIB
 * 프록시가 생겨 기동마다 경고가 찍혔고, 예전 구조에서는 구글 HTTP 호출 동안 DB 커넥션을 붙잡았다.
 */
@Service
public class EarthOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService googleIdToken = new OidcUserService();
    private final GoogleUserRegistrar registrar;

    public EarthOidcUserService(GoogleUserRegistrar registrar) {
        this.registrar = registrar;
        googleIdToken.setRetrieveUserInfo(request -> false);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = googleIdToken.loadUser(userRequest);
        User user = registrar.findOrCreate(oidcUser.getSubject());
        return new EarthOAuth2User(user.getId(), oidcUser.getIdToken());
    }
}
