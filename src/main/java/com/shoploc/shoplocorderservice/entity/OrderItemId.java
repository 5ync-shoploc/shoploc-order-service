package com.shoploc.shoplocorderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Clé primaire composée de ligne_commande : (id_commande, id_produit). */
@Embeddable
public class OrderItemId implements Serializable {

    @Column(name = "id_commande")
    private Long orderId;

    @Column(name = "id_produit")
    private Long productId;

    protected OrderItemId() {
        // requis par JPA
    }

    public OrderItemId(Long orderId, Long productId) {
        this.orderId = orderId;
        this.productId = productId;
    }

    public Long getOrderId() { return orderId; }

    public Long getProductId() { return productId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderItemId that)) {
            return false;
        }
        return Objects.equals(orderId, that.orderId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
}
