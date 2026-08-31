package com.earth.config;

import com.earth.security.jwt.StompAuthChannelInterceptor;
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

    public WebSocketConfig(CorsProperties corsProperties, StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.corsProperties = corsProperties;
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // setAllowedOrigins는 가변인자라 목록을 배열로 펼쳐 넘긴다.
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic/events            -> 전 지구 실시간 신규 이벤트 피드 (누구나 구독 가능)
        // /topic/chat.{eventId}    -> 이벤트별 채팅방 (로그인 + 레벨 조건 충족자만 실제 입장 처리는 서비스단에서 검증)
        // /user/queue/errors    -> 메시지 처리 실패 사유를 보낸 사람에게만 되돌려주는 채널
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
