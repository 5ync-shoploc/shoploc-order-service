package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une commande vue par le commerçant, dans sa liste : seules ses lignes sont comptées.
 *
 * @param itemCount   nombre d'articles du commerçant dans la commande
 * @param totalAmount total des lignes du commerçant
 */
public record MerchantOrderSummaryResponse(
        Long orderId,
        Instant orderDate,
        OrderStatus status,
        String clientId,
        Integer itemCount,
        BigDecimal totalAmount
) {
}
