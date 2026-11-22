package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une commande vue par le commerçant, dans sa liste : seules ses lignes sont comptées.
 *
 * @param itemCount   nombre d'articles du commerçant dans la commande
 * @param totalAmount total des lignes du commerçant
 */
@Schema(description = "Limité aux lignes qui concernent le commerçant connecté.")
public record MerchantOrderSummaryResponse(
        @Schema(example = "1") Long orderId,
        Instant orderDate,
        OrderStatus status,
        @Schema(description = "Identifiant Keycloak du client") String clientId,
        @Schema(example = "3") Integer itemCount,
        @Schema(example = "27.00") BigDecimal totalAmount
) {
}
