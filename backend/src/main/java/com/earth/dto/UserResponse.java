package com.earth.dto;

import com.earth.domain.user.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        int level,
        int exp
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getNickname(),
                user.getProfileImageUrl(), user.getLevel(), user.getExp());
    }
}
