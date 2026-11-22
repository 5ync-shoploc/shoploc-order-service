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
import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.repository.OrderRepository;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final CurrentUser CLIENT = new CurrentUser("client-1", Role.CLIENT);
    private static final CurrentUser MERCHANT = new CurrentUser("merchant-1", Role.COMMERCANT);

    private static final ProductInfo BREAD = new ProductInfo(1L, "Pain", new BigDecimal("3.50"), true, 10, "merchant-1");
    private static final ProductInfo CHEESE = new ProductInfo(3L, "Fromage", new BigDecimal("6.80"), true, 4, "merchant-2");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductCatalogClient catalog;

    @InjectMocks
    private CartService cartService;

    private static Order cartWith(OrderItem... items) {
        Order cart = new Order();
        cart.setClientId("client-1");
        cart.setStatus(OrderStatus.PANIER);
        for (OrderItem item : items) {
            cart.addItem(item);
        }
        cart.recomputeTotal();
        return cart;
    }

    private void noCart() {
        when(orderRepository.findFirstByClientIdAndStatus("client-1", OrderStatus.PANIER)).thenReturn(Optional.empty());
    }

    private void existingCart(Order cart) {
        when(orderRepository.findFirstByClientIdAndStatus("client-1", OrderStatus.PANIER)).thenReturn(Optional.of(cart));
    }

    private void saveReturnsArgument() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- consulter ----------

    @Test
    @DisplayName("Pas encore de panier : un panier vide est renvoyé")
    void getCartWithoutCartReturnsEmptyCart() {
        noCart();

        OrderResponse cart = cartService.getCart(CLIENT);

        assertNull(cart.id());
        assertEquals(OrderStatus.PANIER, cart.status());
        assertTrue(cart.items().isEmpty());
        assertEquals(BigDecimal.ZERO, cart.totalAmount());
    }

    @Test
    @DisplayName("Le panier existant est renvoyé avec ses articles")
    void getCartReturnsExistingCart() {
        existingCart(cartWith(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"))));

        OrderResponse cart = cartService.getCart(CLIENT);

        assertEquals(1, cart.items().size());
        assertEquals(new BigDecimal("7.00"), cart.totalAmount());
    }

    @Test
    @DisplayName("Un commerçant n'a pas de panier : interdit")
    void getCartByMerchantIsForbidden() {
        assertThrows(ForbiddenException.class, () -> cartService.getCart(MERCHANT));
        verifyNoInteractions(orderRepository, catalog);
    }

    // ---------- ajouter ----------

    @Test
    @DisplayName("Premier ajout : le panier est créé avec l'article")
    void addItemCreatesCart() {
        noCart();
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));
        saveReturnsArgument();

        OrderResponse cart = cartService.addItem(CLIENT, new OrderItemRequest(1L, 2));

        assertEquals(OrderStatus.PANIER, cart.status());
        assertEquals(1, cart.items().size());
        assertEquals(new BigDecimal("7.00"), cart.totalAmount());
        verify(catalog, never()).decrementStock(anyMap());
    }

    @Test
    @DisplayName("Ajouter un article déjà présent : les quantités s'additionnent")
    void addItemAlreadyInCartIncreasesQuantity() {
        existingCart(cartWith(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"))));
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));
        saveReturnsArgument(); // le panier de test n'a pas d'id : il est traité comme nouveau

        OrderResponse cart = cartService.addItem(CLIENT, new OrderItemRequest(1L, 3));

        assertEquals(1, cart.items().size());
        assertEquals(5, cart.items().get(0).quantity());
        assertEquals(new BigDecimal("17.50"), cart.totalAmount());
    }

    @Test
    @DisplayName("Ajouter plus que le stock disponible : InsufficientStockException")
    void addItemBeyondStockThrowsInsufficientStock() {
        noCart();
        when(catalog.findProduct(3L)).thenReturn(Optional.of(CHEESE)); // stock 4

        assertThrows(InsufficientStockException.class, () -> cartService.addItem(CLIENT, new OrderItemRequest(3L, 5)));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ajouter un article inconnu : ProductNotFoundException")
    void addUnknownItemThrowsProductNotFound() {
        when(catalog.findProduct(99L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> cartService.addItem(CLIENT, new OrderItemRequest(99L, 1)));
    }

    @Test
    @DisplayName("Ajouter un article inactif : ProductUnavailableException")
    void addInactiveItemThrowsProductUnavailable() {
        when(catalog.findProduct(4L)).thenReturn(Optional.of(new ProductInfo(4L, "Confiture", BigDecimal.ONE, false, 50, "merchant-2")));

        assertThrows(ProductUnavailableException.class, () -> cartService.addItem(CLIENT, new OrderItemRequest(4L, 1)));
    }

    // ---------- modifier / retirer ----------

    @Test
    @DisplayName("Changer la quantité d'un article du panier")
    void updateItemChangesQuantity() {
        existingCart(cartWith(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"))));
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));

        OrderResponse cart = cartService.updateItem(CLIENT, 1L, new UpdateCartItemRequest(4));

        assertEquals(4, cart.items().get(0).quantity());
        assertEquals(new BigDecimal("14.00"), cart.totalAmount());
    }

    @Test
    @DisplayName("Changer la quantité d'un article absent du panier : CartItemNotFoundException")
    void updateItemNotInCartThrowsNotFound() {
        existingCart(cartWith(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"))));

        assertThrows(CartItemNotFoundException.class, () -> cartService.updateItem(CLIENT, 3L, new UpdateCartItemRequest(1)));
    }

    @Test
    @DisplayName("Changer une quantité sans panier : CartItemNotFoundException")
    void updateItemWithoutCartThrowsNotFound() {
        noCart();

        assertThrows(CartItemNotFoundException.class, () -> cartService.updateItem(CLIENT, 1L, new UpdateCartItemRequest(1)));
    }

    @Test
    @DisplayName("Retirer un article : il disparaît du panier")
    void removeItemKeepsOtherItems() {
        existingCart(cartWith(
                new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50")),
                new OrderItem(3L, "Fromage", "merchant-2", 1, new BigDecimal("6.80"))));

        OrderResponse cart = cartService.removeItem(CLIENT, 1L);

        assertEquals(1, cart.items().size());
        assertEquals(new BigDecimal("6.80"), cart.totalAmount());
    }

    @Test
    @DisplayName("Retirer le dernier article : le panier est supprimé")
    void removeLastItemDeletesCart() {
        Order cart = cartWith(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50")));
        existingCart(cart);

        OrderResponse response = cartService.removeItem(CLIENT, 1L);

        assertTrue(response.items().isEmpty());
        verify(orderRepository).delete(cart);
    }

    @Test
    @DisplayName("Retirer un article sans panier : CartItemNotFoundException")
    void removeItemWithoutCartThrowsNotFound() {
        noCart();

        assertThrows(CartItemNotFoundException.class, () -> cartService.removeItem(CLIENT, 1L));
    }

    // ---------- valider ----------

    @Test
    @DisplayName("Valider : statut EN_ATTENTE, prix relus et figés, créneau enregistré, stock décrémenté")
    void validateTurnsCartIntoPendingOrder() {
        // le prix du pain a changé depuis l'ajout au panier : 3.00 -> 3.50
        existingCart(cartWith(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.00"))));
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));
        LocalDateTime slot = LocalDateTime.now().plusDays(1).withNano(0);

        OrderResponse order = cartService.validate(CLIENT, new ValidateCartRequest(slot));

        assertEquals(OrderStatus.EN_ATTENTE, order.status());
        assertEquals(new BigDecimal("3.50"), order.items().get(0).unitPrice());
        assertEquals(new BigDecimal("7.00"), order.totalAmount());
        assertEquals(slot, order.pickupSlot());
        assertNotNull(order.orderDate());
        verify(orderRepository).flush();
        verify(catalog).decrementStock(Map.of(1L, 2));
    }

    @Test
    @DisplayName("Valider sans créneau de retrait")
    void validateWithoutBody() {
        existingCart(cartWith(new OrderItem(1L, "Pain", "merchant-1", 1, new BigDecimal("3.50"))));
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));

        OrderResponse order = cartService.validate(CLIENT, null);

        assertNull(order.pickupSlot());
        assertEquals(OrderStatus.EN_ATTENTE, order.status());
    }

    @Test
    @DisplayName("Valider sans panier : EmptyCartException")
    void validateWithoutCartThrowsEmptyCart() {
        noCart();

        assertThrows(EmptyCartException.class, () -> cartService.validate(CLIENT, null));
        verifyNoInteractions(catalog);
    }

    @Test
    @DisplayName("Valider un panier vide : EmptyCartException")
    void validateEmptyCartThrowsEmptyCart() {
        existingCart(cartWith());

        assertThrows(EmptyCartException.class, () -> cartService.validate(CLIENT, null));
    }

    @Test
    @DisplayName("Valider alors que le stock a baissé : InsufficientStockException, rien n'est décrémenté")
    void validateWithInsufficientStockThrows() {
        existingCart(cartWith(new OrderItem(3L, "Fromage", "merchant-2", 5, new BigDecimal("6.80"))));
        when(catalog.findProduct(3L)).thenReturn(Optional.of(CHEESE)); // stock 4

        assertThrows(InsufficientStockException.class, () -> cartService.validate(CLIENT, null));
        verify(catalog, never()).decrementStock(anyMap());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valider : le nom du client est enregistré dans la commande")
    void validateStoresClientName() {
        Order cart = cartWith(new OrderItem(1L, "Pain", "merchant-1", 1, new BigDecimal("3.50")));
        existingCart(cart);
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));

        cartService.validate(new CurrentUser("client-1", Role.CLIENT, "Obed Dupont"), null);

        assertEquals("Obed Dupont", cart.getClientName());
    }
}
