package com.earth.dev;

import com.earth.config.OAuth2Properties;
import com.earth.domain.user.AuthProvider;
import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.security.jwt.JwtTokenProvider;
import com.earth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * 개발 환경 전용 로그인. 구글 OAuth 클라이언트가 테스트 모드라 등록된 이메일 하나로만
 * 로그인할 수 있어, 실시간 채팅처럼 서로 다른 두 사람이 필요한 기능을 검증할 수 없다.
 * 닉네임만 주면 그 이름의 계정을 만들어(또는 찾아) 로그인시켜 준다.
 *
 * <p><b>local 프로파일에서만 빈이 만들어진다.</b> 운영 배포는 이 프로파일을 켜지 않으므로
 * 이 컨트롤러 자체가 존재하지 않는다. 인증 절차 없이 임의의 계정 토큰을 발급하는 통로이므로
 * 운영에서 활성화하면 안 된다.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("local")
public class DevAuthController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final OAuth2Properties oAuth2Properties;

    public DevAuthController(UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
                             RefreshTokenService refreshTokenService, OAuth2Properties oAuth2Properties) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.oAuth2Properties = oAuth2Properties;
    }

    /**
     * 구글 로그인 성공 시와 완전히 같은 형태로 프론트엔드 리다이렉트 주소에 토큰을 실어 보낸다.
     * 덕분에 프론트엔드는 손댈 것이 없다 — 기존 /oauth2/redirect 화면이 그대로 처리한다.
     */
    @GetMapping("/login")
    @Transactional
    public void login(@RequestParam String nickname, HttpServletResponse response) throws IOException {
        String trimmed = nickname.trim();
        if (trimmed.isEmpty() || trimmed.length() > 50) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "nickname은 1~50자여야 합니다.");
            return;
        }

        // provider_id에 dev: 접두어를 붙여 구글 계정과 절대 충돌하지 않게 한다.
        String providerId = "dev:" + trimmed;
        User user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElseGet(() -> userRepository.save(new User(
                        AuthProvider.GOOGLE, providerId, trimmed + "@dev.local", trimmed, null)));

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        response.sendRedirect(UriComponentsBuilder.fromUriString(oAuth2Properties.redirectUrl())
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString());
    }
}
