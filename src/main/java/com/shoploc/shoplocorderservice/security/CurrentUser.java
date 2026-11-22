package com.shoploc.shoplocorderservice.security;

import com.shoploc.shoplocorderservice.exception.ForbiddenException;

/**
 * Utilisateur à l'origine de la requête.
 *
 * @param name nom affichable (« Prénom Nom »), facultatif : null si l'en-tête X-User-Name est absent
 */
public record CurrentUser(String id, Role role, String name) {

    /** Utilisateur sans nom connu. */
    public CurrentUser(String id, Role role) {
        this(id, role, null);
    }

    public void requireRole(Role expected) {
        if (role != expected) {
            throw new ForbiddenException("Role " + expected + " required");
        }
    }
}
