package com.shoploc.shoplocorderservice.service;

import com.shoploc.shoplocorderservice.catalog.ProductCatalogClient;
import com.shoploc.shoplocorderservice.catalog.ProductInfo;
import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.dto.UpdateCartItemRequest;
import com.shoploc.shoplocorderservice.dto.ValidateCartRequest;
import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.CartItemNotFoundException;
import com.shoploc.shoplocorderservice.exception.EmptyCartException;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.repository.OrderRepository;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panier d'un client : une commande au statut PANIER (au plus une par client).
 * Valider le panier le transforme en commande EN_ATTENTE : prix figés et stock décrémenté.
 */
@Service
public class CartService {

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;

    public CartService(OrderRepository orderRepository, ProductCatalogClient productCatalogClient) {
        this.orderRepository = orderRepository;
        this.productCatalogClient = productCatalogClient;
    }

    /** Le panier du client ; un panier vide s'il n'en a pas. */
    @Transactional(readOnly = true)
    public OrderResponse getCart(CurrentUser user) {
        user.requireRole(Role.CLIENT);
        return orderRepository.findFirstByClientIdAndStatus(user.id(), OrderStatus.PANIER)
                .map(OrderResponse::from)
                .orElseGet(() -> emptyCart(user));
    }

    /** Ajoute un article au panier (crée le panier si besoin ; additionne si l'article y est déjà). */
    @Transactional
    public OrderResponse addItem(CurrentUser user, OrderItemRequest request) {
        user.requireRole(Role.CLIENT);
        ProductInfo product = orderableProduct(request.productId());

        Order cart = orderRepository.findFirstByClientIdAndStatus(user.id(), OrderStatus.PANIER)
                .orElseGet(() -> newCart(user));

        OrderItem existing = cart.findItem(product.id()).orElse(null);
        int newQuantity = request.quantity() + (existing == null ? 0 : existing.getQuantity());
        checkStock(product, newQuantity);

        if (existing == null) {
            cart.addItem(new OrderItem(product.id(), product.name(), product.merchantId(), newQuantity, product.price()));
        } else {
            existing.setQuantity(newQuantity);
            existing.refreshFromCatalog(product.name(), product.merchantId(), product.price());
        }
        cart.recomputeTotal();
        return OrderResponse.from(persist(cart));
    }

    /** Change la quantité d'un article du panier. */
    @Transactional
    public OrderResponse updateItem(CurrentUser user, Long productId, UpdateCartItemRequest request) {
        user.requireRole(Role.CLIENT);
        Order cart = orderRepository.findFirstByClientIdAndStatus(user.id(), OrderStatus.PANIER)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        OrderItem item = cart.findItem(productId).orElseThrow(() -> new CartItemNotFoundException(productId));

        ProductInfo product = orderableProduct(productId);
        checkStock(product, request.quantity());

        item.setQuantity(request.quantity());
        item.refreshFromCatalog(product.name(), product.merchantId(), product.price());
        cart.recomputeTotal();
        return OrderResponse.from(cart);
    }

    /** Retire un article du panier ; le panier est supprimé s'il devient vide. */
    @Transactional
    public OrderResponse removeItem(CurrentUser user, Long productId) {
        user.requireRole(Role.CLIENT);
        Order cart = orderRepository.findFirstByClientIdAndStatus(user.id(), OrderStatus.PANIER)
                .orElseThrow(() -> new CartItemNotFoundException(productId));
        OrderItem item = cart.findItem(productId).orElseThrow(() -> new CartItemNotFoundException(productId));

        cart.removeItem(item);
        if (cart.getItems().isEmpty()) {
            orderRepository.delete(cart);
            return emptyCart(user);
        }
        cart.recomputeTotal();
        return OrderResponse.from(cart);
    }

    /**
     * Valide le panier : il devient une commande EN_ATTENTE.
     * Chaque article est relu dans le catalogue (existant, actif, stock suffisant), son prix est figé,
     * puis le stock est décrémenté. Si le décrément échoue, la transaction annule tout.
     */
    @Transactional
    public OrderResponse validate(CurrentUser user, ValidateCartRequest request) {
        user.requireRole(Role.CLIENT);
        Order cart = orderRepository.findFirstByClientIdAndStatus(user.id(), OrderStatus.PANIER)
                .orElseThrow(EmptyCartException::new);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItem item : cart.getItems()) {
            ProductInfo product = orderableProduct(item.getProductId());
            checkStock(product, item.getQuantity());
            item.refreshFromCatalog(product.name(), product.merchantId(), product.price());
            quantities.put(item.getProductId(), item.getQuantity());
        }

        cart.recomputeTotal();
        cart.setStatus(OrderStatus.EN_ATTENTE);
        cart.setOrderDate(Instant.now());
        cart.setPickupSlot(request == null ? null : request.pickupSlot());
        cart.setClientName(user.name());
        // écrit la commande en base avant d'appeler le catalogue : une erreur SQL arrive avant le décrément du stock
        orderRepository.flush();

        productCatalogClient.decrementStock(quantities);
        return OrderResponse.from(cart);
    }

    /**
     * Un nouveau panier est enregistré avec save().
     * Un panier existant est déjà suivi par Hibernate : ses changements (y compris les nouvelles lignes)
     * sont écrits automatiquement à la fin de la transaction.
     */
    private Order persist(Order cart) {
        return cart.getId() == null ? orderRepository.save(cart) : cart;
    }

    private ProductInfo orderableProduct(Long productId) {
        ProductInfo product = productCatalogClient.findProduct(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (!product.active()) {
            throw new ProductUnavailableException(productId);
        }
        return product;
    }

    private static void checkStock(ProductInfo product, int quantity) {
        if (product.stock() < quantity) {
            throw new InsufficientStockException(product.id(), quantity, product.stock());
        }
    }

    private static Order newCart(CurrentUser user) {
        Order cart = new Order();
        cart.setClientId(user.id());
        cart.setStatus(OrderStatus.PANIER);
        cart.setTotalAmount(BigDecimal.ZERO);
        return cart;
    }

    private static OrderResponse emptyCart(CurrentUser user) {
        return new OrderResponse(null, user.id(), OrderStatus.PANIER, BigDecimal.ZERO, null, null, null, List.of());
    }
}
