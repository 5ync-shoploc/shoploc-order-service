package com.shoploc.shoplocorderservice.dto;

import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;

/** @param pickupSlot créneau de retrait souhaité, facultatif ; s'il est donné, il doit être dans le futur */
public record ValidateCartRequest(@Future LocalDateTime pickupSlot) {
}
