package com.earth.security.jwt;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP CONNECT 프레임의 Authorization 헤더로 JWT를 검증해 STOMP 세션에
 * Principal(userId)을 부여한다. 비로그인 사용자는 Principal 없이 연결되며,
 * /topic/events 같은 공개 피드는 그대로 구독할 수 있다.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                String jwt = token.substring("Bearer ".length());
                if (jwtTokenProvider.isValid(jwt)) {
                    Long userId = jwtTokenProvider.getUserId(jwt);
                    Principal principal = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null);
                    accessor.setUser(principal);
                }
            }
        }
        return message;
    }
}
