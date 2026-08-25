package com.earth.service;

import com.earth.config.ChatProperties;
import com.earth.domain.chat.ChatMessage;
import com.earth.domain.chat.ChatMessageRepository;
import com.earth.domain.event.Event;
import com.earth.domain.user.User;
import com.earth.dto.ChatMessageResponse;
import com.earth.exception.EarthApiException;
import com.earth.exception.ErrorCode;
import com.earth.realtime.RedisMessagePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final EventService eventService;
    private final ChatProperties chatProperties;

    public ChatService(ChatMessageRepository chatMessageRepository,
                        RedisMessagePublisher redisMessagePublisher,
                        EventService eventService,
                        ChatProperties chatProperties) {
        this.chatMessageRepository = chatMessageRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.eventService = eventService;
        this.chatProperties = chatProperties;
    }

    public List<ChatMessageResponse> findRecent(Long eventId) {
        Event event = eventService.getEventOrThrow(eventId);
        List<ChatMessageResponse> recentDesc = chatMessageRepository.findTop100ByEventOrderByCreatedAtDesc(event)
                .stream().map(ChatMessageResponse::from).toList();
        List<ChatMessageResponse> chronological = new java.util.ArrayList<>(recentDesc);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional
    public ChatMessageResponse postMessage(Long eventId, User sender, String content) {
        if (!sender.hasAtLeastLevel(chatProperties.minLevelToJoin())) {
            throw new EarthApiException(ErrorCode.LEVEL_NOT_ENOUGH);
        }
        Event event = eventService.getEventOrThrow(eventId);
        ChatMessage message = new ChatMessage(event, sender, content);
        chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.from(message);
        redisMessagePublisher.publishChatMessage(eventId, response);
        return response;
    }
}
