package com.earth.security.oauth2;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 구글 로그인 직후의 인증 주체. 로그인 성공 처리기({@link OAuth2SuccessHandler})가 Earth 사용자 ID를 꺼내 JWT를
 * 발급하는 데만 쓰고, 그 뒤로는 JWT가 인증을 맡는다. 세션에 남지 않는다.
 */
public class EarthOAuth2User implements OidcUser {

    private final Long userId;
    private final OidcIdToken idToken;

    public EarthOAuth2User(Long userId, OidcIdToken idToken) {
        this.userId = userId;
        this.idToken = idToken;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getClaims() {
        return idToken.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return null;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return idToken.getClaims();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
