package com.earth.realtime;

import com.earth.config.RedisConfig;
import com.earth.dto.ChatMessageResponse;
import com.earth.dto.EventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 여러 백엔드 인스턴스가 떠 있어도 Redis pub/sub을 거쳐 모든 인스턴스에 연결된
 * WebSocket 클라이언트에게 동일한 이벤트/채팅 메시지가 브로드캐스트되도록 하는 릴레이.
 *
 * <p>MessageListener를 직접 구현하는 것이 중요하다. MessageListenerAdapter에 위임 객체로
 * 물리면 어댑터가 리플렉션으로 메서드를 찾기 전에 extractMessage()로 본문을 자기 serializer
 * (기본값이 JDK 직렬화)로 먼저 풀어보는데, 우리 본문은 JSON이라 거기서 예외가 난다.
 * 그 예외는 어댑터 내부에서 잡혀 로그로만 남기 때문에 "메시지가 조용히 사라지는" 형태가 된다.
 */
@Component
public class RedisMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
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
            // 여기서 예외를 던져봐야 리스너 컨테이너가 삼킨다. 반드시 로그로 남겨야 추적이 가능하다.
            log.error("Redis 메시지 릴레이 실패: channel={}, body={}", channel,
                    new String(message.getBody(), StandardCharsets.UTF_8), e);
        }
    }
}
