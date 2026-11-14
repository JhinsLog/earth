package com.earth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 길이 상한은 본문 크기만 막는다. 실제 규칙(2~16자, 허용 문자)은 {@link com.earth.domain.user.Nicknames}가 본다. */
public record NicknameChangeRequest(@NotNull @Size(max = 64) String nickname) {
}
