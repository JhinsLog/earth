package com.earth.config;

import com.earth.security.geo.ViewerCountryHandshakeInterceptor;
import com.earth.security.jwt.StompAuthChannelInterceptor;
import com.earth.security.jwt.StompSubscriptionGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final StompSubscriptionGuard stompSubscriptionGuard;
    private final ViewerCountryHandshakeInterceptor viewerCountryHandshakeInterceptor;

    public WebSocketConfig(CorsProperties corsProperties,
                           StompAuthChannelInterceptor stompAuthChannelInterceptor,
                           StompSubscriptionGuard stompSubscriptionGuard,
                           ViewerCountryHandshakeInterceptor viewerCountryHandshakeInterceptor) {
        this.corsProperties = corsProperties;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.stompSubscriptionGuard = stompSubscriptionGuard;
        this.viewerCountryHandshakeInterceptor = viewerCountryHandshakeInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 순서가 중요하다. 신원(CONNECT)을 먼저 정해야 구독 검사가 로그인 여부를 볼 수 있다.
        registration.interceptors(stompAuthChannelInterceptor, stompSubscriptionGuard);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(viewerCountryHandshakeInterceptor)
                // setAllowedOrigins는 가변인자라 목록을 배열로 펼쳐 넘긴다.
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic/events              -> 전 지구 별 피드, 전체 정보 (로그인 사용자만)
        // /topic/events.public       -> 같은 피드, 위치와 존재만 (누구나)
        // /topic/events.country.{CC} -> 그 나라 별의 전체 정보 (비로그인은 자기 접속 국가만)
        // /topic/chat.{eventId}      -> 이벤트별 채팅방 (로그인 사용자만 구독, 전송은 레벨까지 서비스에서 검증)
        // 구독 권한은 TopicAccessPolicy가 정하고 StompSubscriptionGuard가 강제한다.
        // /user/queue/errors    -> 메시지 처리 실패 사유를 보낸 사람에게만 되돌려주는 채널
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
