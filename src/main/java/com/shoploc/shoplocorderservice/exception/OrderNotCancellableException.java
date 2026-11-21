package com.shoploc.shoplocorderservice.exception;

import com.shoploc.shoplocorderservice.entity.OrderStatus;

/** La commande ne peut plus être annulée par le client (HTTP 409). */
public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(Long orderId, OrderStatus status) {
        super("Order " + orderId + " can no longer be cancelled (status " + status + ")");
    }
}
