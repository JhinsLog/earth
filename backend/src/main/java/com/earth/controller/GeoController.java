package com.earth.controller;

import com.earth.security.geo.ViewerCountryResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo")
public class GeoController {

    /** 서버가 판정한 접속 국가. 판정하지 못하면 null. */
    public record ViewerCountryResponse(String countryCode) {
    }

    private final ViewerCountryResolver viewerCountryResolver;

    public GeoController(ViewerCountryResolver viewerCountryResolver) {
        this.viewerCountryResolver = viewerCountryResolver;
    }

    /**
     * 비로그인 화면이 "어느 나라 별까지 자세히 볼 수 있는지"와 구독할 국가 채널을 정하는 데 쓴다.
     * 판정은 서버가 한다. 클라이언트가 국가를 주장하게 두면 그대로 우회된다.
     */
    @GetMapping("/me")
    public ViewerCountryResponse me(HttpServletRequest request) {
        return new ViewerCountryResponse(
                viewerCountryResolver.resolve(request.getHeader(viewerCountryResolver.headerName())).orElse(null));
    }
}
