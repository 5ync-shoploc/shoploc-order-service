package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Le contenu d'une commande vu par le commerçant : uniquement les lignes de ses articles. */
@Schema(description = "totalAmount ne compte que les lignes du commerçant connecté, pas le total de la commande entière.")
public record MerchantOrderDetailResponse(
        @Schema(example = "1") Long orderId,
        Instant orderDate,
        OrderStatus status,
        @Schema(description = "Identifiant Keycloak du client") String clientId,
        List<OrderItemResponse> items,
        @Schema(example = "27.00") BigDecimal totalAmount
) {
}
