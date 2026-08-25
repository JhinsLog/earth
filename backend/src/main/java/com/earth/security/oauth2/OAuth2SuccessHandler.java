package com.earth.security.oauth2;

import com.earth.config.OAuth2Properties;
import com.earth.security.jwt.JwtTokenProvider;
import com.earth.service.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final OAuth2Properties oAuth2Properties;

    public OAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService,
                                 OAuth2Properties oAuth2Properties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.oAuth2Properties = oAuth2Properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        EarthOAuth2User principal = (EarthOAuth2User) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.createAccessToken(principal.getUserId());
        String refreshToken = jwtTokenProvider.createRefreshToken(principal.getUserId());
        refreshTokenService.save(principal.getUserId(), refreshToken);

        String redirectUrl = UriComponentsBuilder.fromUriString(oAuth2Properties.redirectUrl())
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
