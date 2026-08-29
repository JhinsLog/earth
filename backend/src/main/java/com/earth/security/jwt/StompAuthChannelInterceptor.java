package com.earth.security.jwt;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

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
        // StompHeaderAccessor.wrap()은 헤더를 '복사'해 새 accessor를 만든다. 거기에 setUser를 해도
        // 원본 메시지에는 반영되지 않아 Principal이 조용히 사라진다(연결은 되는데 메시지를 보내면
        // 아무 반응 없이 무시되던 원인). 메시지에 이미 붙어 있는 가변 accessor를 꺼내 써야 한다.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return message;
        }

        String jwt = authorization.substring("Bearer ".length());
        if (!jwtTokenProvider.isValidAccessToken(jwt)) {
            return message;
        }

        Long userId = jwtTokenProvider.getUserId(jwt);
        // 3-arg 생성자여야 authenticated=true로 만들어진다. 2-arg는 미인증 토큰이라
        // 이후 Spring Security 메시지 보안을 붙일 때 그대로 거부된다.
        Principal principal = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of());
        accessor.setUser(principal);
        return message;
    }
}
