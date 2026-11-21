package com.shoploc.shoplocorderservice.catalog;

import java.util.Map;
import java.util.Optional;

/**
 * Accès au service catalogue (implémentation : HttpProductCatalogClient).
 * L'interface permet aux tests de le remplacer par un faux catalogue.
 */
public interface ProductCatalogClient {

    Optional<ProductInfo> findProduct(Long productId);

    /**
     * Décrémente le stock de tous les produits d'un coup : soit tout passe, soit rien.
     *
     * @param quantities productId -> quantité à retirer
     * @throws com.shoploc.shoplocorderservice.exception.InsufficientStockException si un stock est insuffisant
     */
    void decrementStock(Map<Long, Integer> quantities);
}
