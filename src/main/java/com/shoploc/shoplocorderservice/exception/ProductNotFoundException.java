package com.shoploc.shoplocorderservice.exception;

/** Article introuvable dans le catalogue (HTTP 422). */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("Product " + productId + " does not exist");
    }
}
