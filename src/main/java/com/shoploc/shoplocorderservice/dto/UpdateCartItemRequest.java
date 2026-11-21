package com.shoploc.shoplocorderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Nouvelle quantité d'un article du panier (pour le retirer : DELETE). */
public record UpdateCartItemRequest(@NotNull @Positive Integer quantity) {
}
