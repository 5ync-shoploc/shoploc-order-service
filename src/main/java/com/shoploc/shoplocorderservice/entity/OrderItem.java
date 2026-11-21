package com.shoploc.shoplocorderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Table "ligne_commande" du schéma orders (voir le MLD).
 * Clé primaire : (id_commande, id_produit). Un produit apparaît donc au plus une fois par commande.
 * Le nom, le prix et le commerçant sont copiés depuis le catalogue au moment de la commande :
 * si le prix change ensuite, la commande garde l'ancien prix.
 */
@Entity
@Table(name = "ligne_commande")
public class OrderItem {

    @EmbeddedId
    private OrderItemId id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_commande")
    private Order order;

    @Column(name = "nom_produit", nullable = false)
    private String productName;

    /** Identifiant Keycloak du commerçant propriétaire du produit (keycloak-db.utilisateur). */
    @Column(name = "id_commercant", nullable = false)
    private String merchantId;

    @Column(name = "quantite", nullable = false)
    private Integer quantity;

    @Column(name = "prix_unitaire", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // requis par JPA
    }

    public OrderItem(Long productId, String productName, String merchantId, Integer quantity, BigDecimal unitPrice) {
        this.id = new OrderItemId(null, productId);
        this.productName = productName;
        this.merchantId = merchantId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Prix de la ligne = prix unitaire x quantité. */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public OrderItemId getId() { return id; }

    public Order getOrder() { return order; }
    void setOrder(Order order) { this.order = order; }

    public Long getProductId() { return id.getProductId(); }

    public String getProductName() { return productName; }

    public String getMerchantId() { return merchantId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    /** Met à jour le nom, le commerçant et le prix avec les valeurs actuelles du catalogue. */
    public void refreshFromCatalog(String productName, String merchantId, BigDecimal unitPrice) {
        this.productName = productName;
        this.merchantId = merchantId;
        this.unitPrice = unitPrice;
    }

    public BigDecimal getUnitPrice() { return unitPrice; }
}
