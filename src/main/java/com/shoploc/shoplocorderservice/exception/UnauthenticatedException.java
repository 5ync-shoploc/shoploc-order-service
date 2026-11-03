package com.shoploc.shoplocorderservice.exception;

/** Utilisateur non identifié (HTTP 401). */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
