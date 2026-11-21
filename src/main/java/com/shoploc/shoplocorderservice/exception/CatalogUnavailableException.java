package com.shoploc.shoplocorderservice.exception;

/** Le service catalogue ne répond pas, ou répond de façon inattendue (HTTP 503). */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
