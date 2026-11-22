package com.shoploc.shoplocorderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Nouvelle quantité d'un article du panier (pour le retirer : DELETE). */
public record UpdateCartItemRequest(@Schema(example = "3") @NotNull @Positive Integer quantity) {
}
