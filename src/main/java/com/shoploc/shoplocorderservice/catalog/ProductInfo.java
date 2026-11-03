package com.shoploc.shoplocorderservice.catalog;

import java.math.BigDecimal;

/** Ce dont le service commandes a besoin sur un produit du catalogue. */
public record ProductInfo(
        Long id,
        String name,
        BigDecimal price,
        boolean active,
        int stock,
        String merchantId
) {
}
