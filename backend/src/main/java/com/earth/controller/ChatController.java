package com.earth.controller;

import com.earth.domain.user.User;
import com.earth.domain.user.UserRepository;
import com.earth.dto.ChatMessageRequest;
import com.earth.dto.ChatMessageResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    public ChatController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    /** 채팅방 입장 시 최근 대화 내역(최대 100건, 오래된 순)을 REST로 먼저 불러오고, 이후는 STOMP로 실시간 수신한다. */
    @GetMapping
    public List<ChatMessageResponse> history(@PathVariable Long eventId) {
        return chatService.findRecent(eventId);
    }

    /**
     * 클라이언트는 /app/chat.{eventId}.send 로 발행하고, 결과는 Redis를 거쳐
     * /topic/chat.{eventId} 를 구독 중인 모든 인스턴스의 클라이언트에게 전달된다.
     */
    @MessageMapping("/chat.{eventId}.send")
    public void send(@DestinationVariable Long eventId, @Payload ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new EarthApiException(ErrorCode.LOGIN_REQUIRED);
        }
        User sender = userRepository.findById(Long.valueOf(principal.getName()))
                .orElseThrow(() -> new EarthApiException(ErrorCode.USER_NOT_FOUND));
        chatService.postMessage(eventId, sender, request.content());
    }
}
