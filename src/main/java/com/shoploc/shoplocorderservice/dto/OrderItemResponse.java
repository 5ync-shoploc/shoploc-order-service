package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String productName,
        String merchantId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getMerchantId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
