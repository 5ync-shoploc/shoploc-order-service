package com.shoploc.shoplocorderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;

/** @param pickupSlot créneau de retrait souhaité, facultatif ; s'il est donné, il doit être dans le futur */
public record ValidateCartRequest(
        @Schema(description = "Facultatif, format ISO", example = "2026-12-01T18:30:00", nullable = true)
        @Future LocalDateTime pickupSlot) {
}
