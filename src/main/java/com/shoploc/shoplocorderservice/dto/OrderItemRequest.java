package com.shoploc.shoplocorderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @Schema(example = "1") @NotNull Long productId,
        @Schema(example = "2") @NotNull @Positive Integer quantity
) {
}
