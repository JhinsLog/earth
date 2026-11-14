package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.dto.ClientEventBatch;
import com.earth.security.geo.ViewerCountryResolver;
import com.earth.service.ClientEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 브라우저의 오류·행동 로그 수집 창구. 로그인 없이 열려 있다 — 오류는 로그인 전(첫 화면, 로그인 실패)에도 나고,
 * 그때가 가장 알아야 할 때다. 대신 본문 크기(RequestBodyLimitFilter), 필드 길이(DTO), 접속지별 횟수(서비스)로 막는다.
 */
@RestController
public class ClientEventController {

    public static final String PATH = "/api/client-events";
    /**
     * 실제 접속자 IP. Cloudflare Tunnel 뒤라 remoteAddr은 언제나 로컬 cloudflared다. 이 헤더는 Cloudflare가 엣지에서
     * 덮어쓰므로 터널을 거친 요청에서는 위조할 수 없다(CF-IPCountry와 같은 전제, application.yml earth.geo 참고).
     */
    private static final String CLIENT_IP_HEADER = "CF-Connecting-IP";

    private final ClientEventService clientEventService;
    private final ViewerCountryResolver countryResolver;

    public ClientEventController(ClientEventService clientEventService, ViewerCountryResolver countryResolver) {
        this.clientEventService = clientEventService;
        this.countryResolver = countryResolver;
    }

    @PostMapping(PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void collect(@Valid @RequestBody ClientEventBatch batch,
                        @AuthenticationPrincipal User user,
                        HttpServletRequest request) {
        String ip = request.getHeader(CLIENT_IP_HEADER);
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        String country = countryResolver.resolve(request.getHeader(countryResolver.headerName())).orElse(null);
        clientEventService.record(batch, ip, user == null ? null : user.getId(), country);
    }
}
