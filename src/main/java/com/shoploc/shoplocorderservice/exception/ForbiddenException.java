package com.shoploc.shoplocorderservice.exception;

/** Utilisateur identifié mais sans le bon rôle (HTTP 403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
