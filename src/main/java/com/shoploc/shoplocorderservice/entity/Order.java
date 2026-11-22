package com.shoploc.shoplocorderservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Table "commande" du schéma orders (voir le MLD).
 * id_client et id_paiement référencent d'autres services : pas de clé étrangère SQL.
 */
@Entity
@Table(name = "commande")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_commande")
    private Long id;

    @Column(name = "date_commande", nullable = false)
    private Instant orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "montant_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Créneau de retrait choisi par le client (facultatif pour l'instant). */
    @Column(name = "creneau_retrait")
    private LocalDateTime pickupSlot;

    /** Identifiant Keycloak du client (keycloak-db.utilisateur). */
    @Column(name = "id_client", nullable = false)
    private String clientId;

    /** Nom du client au moment de la commande, figé comme sur un ticket (absent pour les anciennes commandes). */
    @Column(name = "nom_client", length = 200)
    private String clientName;

    /** Référence vers payment.paiement : vide tant que la commande n'est pas payée. */
    @Column(name = "id_paiement")
    private Long paymentId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.orderDate = Instant.now();
    }

    /** Ajoute une ligne et garde les deux côtés de la relation synchronisés. */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    /** Ligne de commande d'un produit, si elle existe. */
    public Optional<OrderItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    /** Retire la ligne d'un produit (elle est supprimée en base grâce à orphanRemoval). */
    public void removeItem(OrderItem item) {
        items.remove(item);
    }

    /** Recalcule le total à partir des lignes. */
    public void recomputeTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }

    public Instant getOrderDate() { return orderDate; }
    public void setOrderDate(Instant orderDate) { this.orderDate = orderDate; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDateTime getPickupSlot() { return pickupSlot; }
    public void setPickupSlot(LocalDateTime pickupSlot) { this.pickupSlot = pickupSlot; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public List<OrderItem> getItems() { return items; }
}
