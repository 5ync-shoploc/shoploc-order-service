package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** Une ligne de commande vue par le commerçant (uniquement ses articles). */
public record MerchantOrderItemResponse(
        @Schema(example = "1") Long orderId,
        OrderStatus orderStatus,
        Instant orderedAt,
        @Schema(example = "1") Long productId,
        @Schema(example = "Pain au levain") String productName,
        @Schema(example = "2") Integer quantity,
        @Schema(description = "Prix au moment de la commande (figé)", example = "4.50") BigDecimal unitPrice,
        @Schema(description = "quantity * unitPrice", example = "9.00") BigDecimal lineTotal
) {
    public static MerchantOrderItemResponse from(OrderItem item) {
        Order order = item.getOrder();
        return new MerchantOrderItemResponse(
                order.getId(),
                order.getStatus(),
                order.getOrderDate(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
