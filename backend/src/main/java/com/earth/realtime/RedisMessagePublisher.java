package com.earth.realtime;

import com.earth.config.RedisConfig;
import com.earth.dto.ChatMessageResponse;
import com.earth.dto.FlareResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisMessagePublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishNewFlare(FlareResponse flare) {
        redisTemplate.convertAndSend(RedisConfig.FLARE_CHANNEL, flare);
    }

    public void publishChatMessage(Long flareId, ChatMessageResponse message) {
        redisTemplate.convertAndSend(RedisConfig.CHAT_CHANNEL_PREFIX + flareId, message);
    }
}
