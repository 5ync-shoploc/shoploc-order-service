package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Une ligne de commande vue par le commerçant (uniquement ses articles). */
public record MerchantOrderItemResponse(
        Long orderId,
        OrderStatus orderStatus,
        Instant orderedAt,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static MerchantOrderItemResponse from(OrderItem item) {
        Order order = item.getOrder();
        return new MerchantOrderItemResponse(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
