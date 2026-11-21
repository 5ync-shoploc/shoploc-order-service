package com.shoploc.shoplocorderservice.exception;

/** Article absent du panier (HTTP 404). */
public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(Long productId) {
        super("Product " + productId + " is not in the cart");
    }
}
