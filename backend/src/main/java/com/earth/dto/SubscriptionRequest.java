package com.earth.dto;

import jakarta.validation.constraints.*;

public record SubscriptionRequest(
        @NotBlank @Size(max = 50) String label,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @Positive Double radiusKm
) {
}
