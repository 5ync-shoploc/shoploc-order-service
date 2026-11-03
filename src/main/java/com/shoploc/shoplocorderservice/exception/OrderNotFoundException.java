package com.shoploc.shoplocorderservice.exception;

/** Commande introuvable, ou appartenant à un autre client (HTTP 404). */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order " + orderId + " not found");
    }
}
