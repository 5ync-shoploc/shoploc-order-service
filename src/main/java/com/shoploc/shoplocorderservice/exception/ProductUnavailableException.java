package com.shoploc.shoplocorderservice.exception;

/** Article existant mais inactif (HTTP 422). */
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(Long productId) {
        super("Product " + productId + " is not available");
    }
}
