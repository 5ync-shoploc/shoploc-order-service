package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Le contenu d'une commande vu par le commerçant : uniquement les lignes de ses articles. */
public record MerchantOrderDetailResponse(
        Long orderId,
        Instant orderDate,
        OrderStatus status,
        String clientId,
        String clientName,
        List<OrderItemResponse> items,
        BigDecimal totalAmount
) {
}
