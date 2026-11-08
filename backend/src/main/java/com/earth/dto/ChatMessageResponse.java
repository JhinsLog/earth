package com.earth.dto;

import com.earth.domain.chat.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id, Long flareId, Long userId, String nickname, String content, Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage chatMessage) {
        return new ChatMessageResponse(
                chatMessage.getId(), chatMessage.getFlare().getId(),
                chatMessage.getUser().getId(), chatMessage.getUser().getNickname(),
                chatMessage.getContent(), chatMessage.getCreatedAt());
    }
}
