package com.earth.security.jwt;

import com.earth.realtime.TopicAccessPolicy;
import com.earth.security.geo.ViewerCountryHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 구독(SUBSCRIBE) 프레임을 {@link TopicAccessPolicy}로 검사한다. 신원은 {@link StompAuthChannelInterceptor}가
 * CONNECT 때 정한 Principal이고, 접속 국가는 연결 시점에 세션에 남겨 둔 값이다.
 *
 * <p>거부한 구독은 예외 대신 프레임을 버린다(null 반환). 예외를 던지면 STOMP ERROR 프레임과 함께 연결이
 * 끊겨, 허용된 구독(공개 피드)까지 같이 잃는다.
 */
@Component
public class StompSubscriptionGuard implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompSubscriptionGuard.class);

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        Map<String, Object> session = accessor.getSessionAttributes();
        String viewerCountry = session == null
                ? null
                : (String) session.get(ViewerCountryHandshakeInterceptor.VIEWER_COUNTRY_ATTRIBUTE);
        String destination = accessor.getDestination();
        if (TopicAccessPolicy.canSubscribe(accessor.getUser() != null, viewerCountry, destination)) {
            return message;
        }
        log.debug("구독 거부: destination={}, viewerCountry={}", destination, viewerCountry);
        return null;
    }
}
