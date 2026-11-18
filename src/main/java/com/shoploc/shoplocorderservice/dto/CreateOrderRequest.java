package com.shoploc.shoplocorderservice.dto;

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
        @Future LocalDateTime pickupSlot
) {
}
