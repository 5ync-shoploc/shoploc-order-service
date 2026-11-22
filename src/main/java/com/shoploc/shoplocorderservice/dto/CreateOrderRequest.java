package com.shoploc.shoplocorderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param items      les articles commandés (au moins un)
 * @param pickupSlot créneau de retrait souhaité, facultatif ; s'il est donné, il doit être dans le futur
 */
public record CreateOrderRequest(
        @NotEmpty List<@Valid OrderItemRequest> items,

        @Schema(description = "Facultatif, format ISO", example = "2026-12-01T18:30:00", nullable = true)
        @Future LocalDateTime pickupSlot
) {
}
