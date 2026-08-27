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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private static final String FLOOD_KEY_PREFIX = "earth:chat-rate:";
    private static final Duration FLOOD_WINDOW = Duration.ofMinutes(1);

    private final ChatMessageRepository chatMessageRepository;
    private final RedisMessagePublisher redisMessagePublisher;
    private final EventService eventService;
    private final ChatProperties chatProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public ChatService(ChatMessageRepository chatMessageRepository,
                        RedisMessagePublisher redisMessagePublisher,
                        EventService eventService,
                        ChatProperties chatProperties,
                        StringRedisTemplate stringRedisTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.redisMessagePublisher = redisMessagePublisher;
        this.eventService = eventService;
        this.chatProperties = chatProperties;
        this.stringRedisTemplate = stringRedisTemplate;
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
        checkFloodLimit(sender);
        Event event = eventService.getEventOrThrow(eventId);
        ChatMessage message = new ChatMessage(event, sender, content);
        chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.from(message);
        redisMessagePublisher.publishChatMessage(eventId, response);
        return response;
    }

    /**
     * 1분 단위 고정 윈도우로 도배를 막는다.
     *
     * <p>Redis 카운터를 쓰는 이유는 두 가지다. 인스턴스를 여러 대로 늘려도 제한이 그대로
     * 유지되고, 매 메시지마다 DB를 조회하지 않아도 된다. 첫 증가 때만 TTL을 걸어
     * 키가 알아서 사라지게 한다.
     */
    private void checkFloodLimit(User sender) {
        String key = FLOOD_KEY_PREFIX + sender.getId();
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) return; // Redis가 응답하지 않는 상황에서 채팅까지 막지는 않는다

        if (count == 1L) {
            stringRedisTemplate.expire(key, FLOOD_WINDOW);
        }
        if (count > chatProperties.maxPerMinute()) {
            throw new EarthApiException(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED,
                    "1분에 최대 %d개까지 보낼 수 있습니다.".formatted(chatProperties.maxPerMinute()));
        }
    }
}
