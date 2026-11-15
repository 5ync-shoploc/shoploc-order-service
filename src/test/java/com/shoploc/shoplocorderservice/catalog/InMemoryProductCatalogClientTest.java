package com.shoploc.shoplocorderservice.catalog;

import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryProductCatalogClientTest {

    private final InMemoryProductCatalogClient catalog = new InMemoryProductCatalogClient();

    @Test
    @DisplayName("Recherche d'un article : trouvé ou vide")
    void findProductReturnsDemoProductOrEmpty() {
        assertTrue(catalog.findProduct(1L).isPresent());
        assertFalse(catalog.findProduct(999L).isPresent());
    }

    @Test
    @DisplayName("Décrément du stock de plusieurs articles")
    void decrementStockDecrementsAllProducts() {
        catalog.decrementStock(Map.of(1L, 10, 3L, 2));

        assertEquals(90, catalog.findProduct(1L).orElseThrow().stock());
        assertEquals(18, catalog.findProduct(3L).orElseThrow().stock());
    }

    @Test
    @DisplayName("Un stock insuffisant : aucun stock n'est modifié (tout ou rien)")
    void decrementStockChangesNothingWhenOneStockIsInsufficient() {
        assertThrows(InsufficientStockException.class,
                () -> catalog.decrementStock(Map.of(1L, 10, 2L, 6)));

        assertEquals(100, catalog.findProduct(1L).orElseThrow().stock());
        assertEquals(5, catalog.findProduct(2L).orElseThrow().stock());
    }

    @Test
    @DisplayName("Décrément d'un article inconnu : exception")
    void decrementStockOfUnknownProductThrows() {
        assertThrows(ProductNotFoundException.class, () -> catalog.decrementStock(Map.of(999L, 1)));
    }

    @Test
    @DisplayName("Réinitialisation du stock de démonstration")
    void resetRestoresDemoStock() {
        catalog.decrementStock(Map.of(2L, 5));
        catalog.reset();

        assertEquals(5, catalog.findProduct(2L).orElseThrow().stock());
    }
}
