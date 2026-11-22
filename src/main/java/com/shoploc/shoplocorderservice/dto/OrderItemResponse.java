package com.shoploc.shoplocorderservice.dto;

import com.shoploc.shoplocorderservice.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record OrderItemResponse(
        @Schema(example = "1") Long productId,
        @Schema(example = "Pain au levain") String productName,
        @Schema(description = "Identifiant Keycloak du commerçant propriétaire") String merchantId,
        @Schema(example = "2") Integer quantity,
        @Schema(description = "Prix au moment de la commande (figé)", example = "4.50") BigDecimal unitPrice,
        @Schema(description = "quantity * unitPrice", example = "9.00") BigDecimal lineTotal
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
