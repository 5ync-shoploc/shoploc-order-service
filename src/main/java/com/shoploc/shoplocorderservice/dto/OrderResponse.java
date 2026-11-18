package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String clientId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant orderDate,
        LocalDateTime pickupSlot,
        Long paymentId,
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
