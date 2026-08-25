package com.earth.realtime;

import com.earth.config.RedisConfig;
import com.earth.dto.ChatMessageResponse;
import com.earth.dto.EventResponse;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 여러 백엔드 인스턴스가 떠 있어도 Redis pub/sub을 거쳐 모든 인스턴스에 연결된
 * WebSocket 클라이언트에게 동일한 이벤트/채팅 메시지가 브로드캐스트되도록 하는 릴레이.
 */
@Component
public class RedisMessageSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void onMessage(Message message, String pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        try {
            if (channel.equals(RedisConfig.EVENT_CHANNEL)) {
                EventResponse event = objectMapper.readValue(message.getBody(), EventResponse.class);
                messagingTemplate.convertAndSend("/topic/events", event);
            } else if (channel.startsWith(RedisConfig.CHAT_CHANNEL_PREFIX)) {
                String eventId = channel.substring(RedisConfig.CHAT_CHANNEL_PREFIX.length());
                ChatMessageResponse chatMessage = objectMapper.readValue(message.getBody(), ChatMessageResponse.class);
                messagingTemplate.convertAndSend("/topic/chat." + eventId, chatMessage);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Redis 메시지 역직렬화 실패: channel=" + channel, e);
        }
    }
}
