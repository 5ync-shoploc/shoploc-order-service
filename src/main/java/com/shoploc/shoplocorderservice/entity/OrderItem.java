package com.shoploc.shoplocorderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Ligne de commande. Le nom, le prix et le commerçant sont copiés depuis le catalogue
 * au moment de la commande : si le prix change ensuite, la commande garde l'ancien prix.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // requis par JPA
    }

    public OrderItem(Long productId, String productName, String merchantId, Integer quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.merchantId = merchantId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /** Prix de la ligne = prix unitaire x quantité. */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }

    public Order getOrder() { return order; }
    void setOrder(Order order) { this.order = order; }

    public Long getProductId() { return productId; }

    public String getProductName() { return productName; }

    public String getMerchantId() { return merchantId; }

    public Integer getQuantity() { return quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
}
