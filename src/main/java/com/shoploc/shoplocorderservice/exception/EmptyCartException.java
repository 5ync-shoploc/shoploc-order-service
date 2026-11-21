package com.shoploc.shoplocorderservice.exception;

/** Validation d'un panier vide ou inexistant (HTTP 400). */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("The cart is empty");
    }
}
