package com.shoploc.shoplocorderservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrentUserArgumentResolverTest {

    @Test
    @DisplayName("Nom encodé avec accents : décodé")
    void decodeNameWithAccents() {
        assertEquals("Hélène Dupré", CurrentUserArgumentResolver.decodeName("H%C3%A9l%C3%A8ne%20Dupr%C3%A9"));
    }

    @Test
    @DisplayName("Nom absent, vide ou mal encodé : null (jamais bloquant)")
    void decodeNameMissingOrInvalidReturnsNull() {
        assertNull(CurrentUserArgumentResolver.decodeName(null));
        assertNull(CurrentUserArgumentResolver.decodeName("   "));
        assertNull(CurrentUserArgumentResolver.decodeName("%20%20"));
        assertNull(CurrentUserArgumentResolver.decodeName("%E9%"));
    }

    @Test
    @DisplayName("Nom trop long : tronqué à 200 caractères")
    void decodeNameTooLongIsTruncated() {
        assertEquals(200, CurrentUserArgumentResolver.decodeName("a".repeat(300)).length());
    }
}
