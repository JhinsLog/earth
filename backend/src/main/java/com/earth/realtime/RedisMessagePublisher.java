package com.earth.realtime;

import com.earth.config.RedisConfig;
import com.earth.dto.ChatMessageResponse;
import com.earth.dto.EventResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisMessagePublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishNewEvent(EventResponse event) {
        redisTemplate.convertAndSend(RedisConfig.EVENT_CHANNEL, event);
    }

    public void publishChatMessage(Long eventId, ChatMessageResponse message) {
        redisTemplate.convertAndSend(RedisConfig.CHAT_CHANNEL_PREFIX + eventId, message);
    }
}
