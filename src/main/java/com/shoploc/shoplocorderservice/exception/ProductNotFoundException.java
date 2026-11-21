package com.shoploc.shoplocorderservice.exception;

/** Article introuvable dans le catalogue (HTTP 422). */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("Product " + productId + " does not exist");
    }

    /** Produit inconnu signalé par le service catalogue (message renvoyé tel quel). */
    public ProductNotFoundException(String message) {
        super(message);
    }
}
