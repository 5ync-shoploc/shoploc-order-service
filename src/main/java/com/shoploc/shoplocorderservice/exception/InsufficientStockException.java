package com.shoploc.shoplocorderservice.exception;

/** Stock insuffisant pour un article (HTTP 409). */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("Insufficient stock for product " + productId
                + ": requested " + requested + ", available " + available);
    }
}
