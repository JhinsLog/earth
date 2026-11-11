package com.earth.dto;

import com.earth.domain.user.UserBlock;

import java.time.Instant;

public record BlockedUserResponse(Long userId, String nickname, Instant blockedAt) {
    public static BlockedUserResponse from(UserBlock block) {
        return new BlockedUserResponse(block.getBlocked().getId(), block.getBlocked().getNickname(), block.getCreatedAt());
    }
}
