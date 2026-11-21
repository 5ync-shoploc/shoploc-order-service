package com.shoploc.shoplocorderservice.catalog;

import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FAUX CATALOGUE, utilisé uniquement par les tests (il n'existe pas dans l'application).
 * Les tests ne doivent pas dépendre du vrai service catalogue : ce faux catalogue garde quelques produits en mémoire.
 *   1  Pain de campagne   3.50   actif    stock 100  merchant-1
 *   2  Croissant          1.20   actif    stock 5    merchant-1
 *   3  Fromage de chèvre  6.80   actif    stock 20   merchant-2
 *   4  Confiture          4.00   INACTIF  stock 50   merchant-2
 */
public class FakeProductCatalogClient implements ProductCatalogClient {

    private final Map<Long, ProductInfo> products = new ConcurrentHashMap<>();

    public FakeProductCatalogClient() {
        reset();
    }

    /** Remet les produits et le stock de départ (à appeler avant chaque test). */
    public final synchronized void reset() {
        products.clear();
        add(new ProductInfo(1L, "Pain de campagne", new BigDecimal("3.50"), true, 100, "merchant-1"));
        add(new ProductInfo(2L, "Croissant", new BigDecimal("1.20"), true, 5, "merchant-1"));
        add(new ProductInfo(3L, "Fromage de chèvre", new BigDecimal("6.80"), true, 20, "merchant-2"));
        add(new ProductInfo(4L, "Confiture", new BigDecimal("4.00"), false, 50, "merchant-2"));
    }

    private void add(ProductInfo product) {
        products.put(product.id(), product);
    }

    @Override
    public Optional<ProductInfo> findProduct(Long productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public synchronized void decrementStock(Map<Long, Integer> quantities) {
        // 1) tout vérifier avant de modifier quoi que ce soit
        quantities.forEach((productId, quantity) -> {
            ProductInfo product = findProduct(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            if (product.stock() < quantity) {
                throw new InsufficientStockException(productId, quantity, product.stock());
            }
        });
        // 2) puis appliquer
        quantities.forEach((productId, quantity) -> {
            ProductInfo p = products.get(productId);
            add(new ProductInfo(p.id(), p.name(), p.price(), p.active(), p.stock() - quantity, p.merchantId()));
        });
    }
}
