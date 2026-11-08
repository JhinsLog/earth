package com.earth.dto;

import com.earth.domain.flare.FlareCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 플레어 수정 요청. 위치는 바꿀 수 없다 — 플레어는 "그 장소에서 일어난 일"이므로
 * 위치를 옮기면 다른 플레어가 된다.
 */
public record FlareUpdateRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 1000) String content,
        @NotNull FlareCategory category
) {
}
