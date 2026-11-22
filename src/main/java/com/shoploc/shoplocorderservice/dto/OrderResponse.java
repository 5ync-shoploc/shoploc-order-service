package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Un panier est une commande au statut PANIER ; id est null tant qu'il n'a jamais été sauvegardé.")
public record OrderResponse(
        @Schema(example = "1", nullable = true) Long id,
        @Schema(description = "Identifiant Keycloak du client") String clientId,
        OrderStatus status,
        @Schema(example = "9.00") BigDecimal totalAmount,
        @Schema(nullable = true) Instant orderDate,
        @Schema(description = "Facultatif", example = "2026-12-01T18:30:00", nullable = true) LocalDateTime pickupSlot,
        @Schema(nullable = true) Long paymentId,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getClientId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderDate(),
                order.getPickupSlot(),
                order.getPaymentId(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
