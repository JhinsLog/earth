package com.earth.dto;

import com.earth.domain.flare.FlareCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 플레어 생성 요청.
 *
 * <p>내용(content)이 있어야 플레어가 확정된다. 우클릭으로 찍은 임시 플레어는 이 요청이 성공해야
 * 실제 플레어가 되고 다른 사용자에게도 보인다.
 */
public record FlareCreateRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 1000) String content,
        @NotNull FlareCategory category,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
) {
}
