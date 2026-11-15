package com.earth.dto;

import com.earth.domain.user.User;

import java.time.Instant;

/**
 * 내 정보.
 *
 * @param nicknameChangeableAt 닉네임을 다른 이름으로 다시 바꿀 수 있는 시각. 지금 바꿀 수 있으면 null.
 *                             설정 화면이 미리 안내하는 데 쓴다(최종 판정은 서버).
 */
public record UserResponse(
        Long id,
        String nickname,
        int level,
        int exp,
        Instant nicknameChangeableAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getNickname(), user.getLevel(), user.getExp(),
                user.nicknameChangeableAt(Instant.now()));
    }
}
