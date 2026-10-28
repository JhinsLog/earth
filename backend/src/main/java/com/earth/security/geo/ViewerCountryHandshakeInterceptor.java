package com.earth.security.geo;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 연결을 맺을 때 접속 국가를 세션 속성에 남긴다.
 *
 * <p>STOMP 프레임에는 Cloudflare가 붙인 HTTP 헤더가 없다. 헤더가 있는 건 연결을 여는 HTTP 요청뿐이라
 * 그 순간에 읽어 두고, 구독 검사({@code StompSubscriptionGuard})가 꺼내 쓴다.
 */
@Component
public class ViewerCountryHandshakeInterceptor implements HandshakeInterceptor {

    public static final String VIEWER_COUNTRY_ATTRIBUTE = "earth.viewerCountry";

    private final ViewerCountryResolver viewerCountryResolver;

    public ViewerCountryHandshakeInterceptor(ViewerCountryResolver viewerCountryResolver) {
        this.viewerCountryResolver = viewerCountryResolver;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        viewerCountryResolver.resolve(request.getHeaders().getFirst(viewerCountryResolver.headerName()))
                .ifPresent(country -> attributes.put(VIEWER_COUNTRY_ATTRIBUTE, country));
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
    }
}
