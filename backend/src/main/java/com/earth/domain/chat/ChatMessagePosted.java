package com.earth.domain.chat;

import com.earth.dto.ChatMessageResponse;

/**
 * 채팅 메시지가 저장되었음을 알리는 도메인 이벤트.
 *
 * <p>별의 {@code EventChanged}와 같은 이유로 존재한다. 트랜잭션 안에서 Redis로 직접 내보내면
 * 이후 단계가 실패해 롤백되었을 때 저장되지 않은 메시지가 채팅방에 남는다. pub/sub에는
 * 롤백이 없어서 되돌릴 수 없고, 상대방 화면에서는 상대가 다시 들어와야 사라진다.
 *
 * @param eventId  메시지가 속한 별. 채널이 별마다 나뉘어 있어 전파에 필요하다.
 * @param snapshot 전파할 응답 형태.
 */
public record ChatMessagePosted(Long eventId, ChatMessageResponse snapshot) {
}
