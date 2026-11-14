package com.earth.dto;

import com.earth.domain.user.User;

public record UserResponse(
        Long id,
        String nickname,
        int level,
        int exp
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getNickname(), user.getLevel(), user.getExp());
    }
}
