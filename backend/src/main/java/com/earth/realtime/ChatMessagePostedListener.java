package com.earth.realtime;

import com.earth.domain.chat.ChatMessagePosted;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 채팅 메시지를 커밋된 뒤에 전파한다.
 *
 * <p>트랜잭션 안에서 내보내면 롤백되었을 때 저장되지 않은 메시지가 채팅방에 남는다.
 * 사유는 {@link ChatMessagePosted} 주석에 적어두었다.
 */
@Component
public class ChatMessagePostedListener {

    private final RedisMessagePublisher redisMessagePublisher;

    public ChatMessagePostedListener(RedisMessagePublisher redisMessagePublisher) {
        this.redisMessagePublisher = redisMessagePublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessagePosted(ChatMessagePosted posted) {
        redisMessagePublisher.publishChatMessage(posted.eventId(), posted.snapshot());
    }
}
