package com.shoploc.shoplocorderservice.security;

import com.shoploc.shoplocorderservice.exception.ForbiddenException;

/** Utilisateur à l'origine de la requête. */
public record CurrentUser(String id, Role role) {

    public void requireRole(Role expected) {
        if (role != expected) {
            throw new ForbiddenException("Role " + expected + " required");
        }
    }
}
