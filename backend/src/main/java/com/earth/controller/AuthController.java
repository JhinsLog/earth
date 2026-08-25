package com.earth.controller;

import com.earth.dto.TokenRefreshRequest;
import com.earth.dto.TokenResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.security.jwt.JwtTokenProvider;
import com.earth.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthController(JwtTokenProvider jwtTokenProvider, RefreshTokenService refreshTokenService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new EarthApiException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        if (!refreshTokenService.matches(userId, refreshToken)) {
            throw new EarthApiException(ErrorCode.INVALID_TOKEN, "이미 폐기된 refresh token입니다.");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken);
        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof com.earth.domain.user.User user) {
            refreshTokenService.revoke(user.getId());
        }
    }
}
