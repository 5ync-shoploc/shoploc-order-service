package com.shoploc.shoplocorderservice.service;

import com.shoploc.shoplocorderservice.catalog.ProductCatalogClient;
import com.shoploc.shoplocorderservice.catalog.ProductInfo;
import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.OrderNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.repository.OrderItemRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final CurrentUser CLIENT = new CurrentUser("client-1", Role.CLIENT);
    private static final CurrentUser MERCHANT = new CurrentUser("merchant-1", Role.COMMERCANT);

    private static final ProductInfo BREAD =
            new ProductInfo(1L, "Pain", new BigDecimal("3.50"), true, 10, "merchant-1");
    private static final ProductInfo CHEESE =
            new ProductInfo(3L, "Fromage", new BigDecimal("6.80"), true, 4, "merchant-2");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductCatalogClient catalog;

    @InjectMocks
    private OrderService orderService;

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items), null);
    }

    // ---------- US1 : création ----------

    @Test
    @DisplayName("Création : prix figés, total calculé, statut EN_ATTENTE, stock décrémenté")
    void createOrderFreezesPricesComputesTotalSetsPendingAndDecrementsStock() {
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));
        when(catalog.findProduct(3L)).thenReturn(Optional.of(CHEESE));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(CLIENT, request(
                new OrderItemRequest(1L, 2),     // 2 x 3.50 = 7.00
                new OrderItemRequest(3L, 1)));   // 1 x 6.80 = 6.80

        assertEquals("client-1", response.clientId());
        assertEquals(OrderStatus.EN_ATTENTE, response.status());
        assertEquals(new BigDecimal("13.80"), response.totalAmount());
        assertEquals(new BigDecimal("3.50"), response.items().get(0).unitPrice());
        assertEquals("merchant-2", response.items().get(1).merchantId());
        verify(catalog).decrementStock(Map.of(1L, 2, 3L, 1));
    }

    @Test
    @DisplayName("Création : le même article deux fois, les quantités sont additionnées")
    void createOrderMergesQuantitiesOfSameProduct() {
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(CLIENT, request(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(1L, 3)));

        assertEquals(1, response.items().size());
        assertEquals(5, response.items().get(0).quantity());
        verify(catalog).decrementStock(Map.of(1L, 5));
    }

    @Test
    @DisplayName("Création : le créneau de retrait est enregistré dans la commande")
    void createOrderKeepsPickupSlot() {
        when(catalog.findProduct(1L)).thenReturn(Optional.of(BREAD));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDateTime slot = LocalDateTime.now().plusDays(1).withNano(0);

        OrderResponse response = orderService.createOrder(CLIENT,
                new CreateOrderRequest(List.of(new OrderItemRequest(1L, 1)), slot));

        assertEquals(slot, response.pickupSlot());
        assertEquals(null, response.paymentId());
    }

    @Test
    @DisplayName("Création par un commerçant : interdit")
    void createOrderByMerchantIsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> orderService.createOrder(MERCHANT, request(new OrderItemRequest(1L, 1))));
        verifyNoInteractions(catalog, orderRepository);
    }

    @Test
    @DisplayName("Création avec un article inconnu : ProductNotFoundException")
    void createOrderWithUnknownProductThrowsProductNotFound() {
        when(catalog.findProduct(99L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> orderService.createOrder(CLIENT, request(new OrderItemRequest(99L, 1))));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Création avec un article inactif : ProductUnavailableException")
    void createOrderWithInactiveProductThrowsProductUnavailable() {
        when(catalog.findProduct(4L)).thenReturn(Optional.of(
                new ProductInfo(4L, "Confiture", BigDecimal.ONE, false, 50, "merchant-2")));

        assertThrows(ProductUnavailableException.class,
                () -> orderService.createOrder(CLIENT, request(new OrderItemRequest(4L, 1))));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Création avec un stock insuffisant : InsufficientStockException")
    void createOrderWithInsufficientStockThrowsInsufficientStock() {
        when(catalog.findProduct(3L)).thenReturn(Optional.of(CHEESE)); // stock 4

        assertThrows(InsufficientStockException.class,
                () -> orderService.createOrder(CLIENT, request(new OrderItemRequest(3L, 5))));
        verify(orderRepository, never()).save(any());
    }

    // ---------- US2 : mes commandes ----------

    @Test
    @DisplayName("Mes commandes : uniquement celles du client connecté")
    void listMyOrdersReturnsOnlyOrdersOfCurrentClient() {
        Order order = new Order();
        order.setClientId("client-1");
        order.setStatus(OrderStatus.EN_ATTENTE);
        order.setTotalAmount(BigDecimal.TEN);
        when(orderRepository.findByClientIdOrderByIdDesc("client-1")).thenReturn(List.of(order));

        List<OrderResponse> orders = orderService.listMyOrders(CLIENT);

        assertEquals(1, orders.size());
        assertEquals("client-1", orders.get(0).clientId());
    }

    @Test
    @DisplayName("Détail de la commande d'un autre client : OrderNotFoundException")
    void getMyOrderOfAnotherClientThrowsNotFound() {
        when(orderRepository.findByIdAndClientId(5L, "client-1")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getMyOrder(CLIENT, 5L));
    }

    @Test
    @DisplayName("Mes commandes demandées par un commerçant : interdit")
    void listMyOrdersByMerchantIsForbidden() {
        assertThrows(ForbiddenException.class, () -> orderService.listMyOrders(MERCHANT));
    }

    // ---------- US3 : vue commerçant ----------

    @Test
    @DisplayName("Vue commerçant : uniquement les lignes du commerçant connecté")
    void listMerchantOrderItemsReturnsOnlyItemsOfCurrentMerchant() {
        Order order = new Order();
        order.setClientId("client-1");
        order.setStatus(OrderStatus.EN_ATTENTE);
        OrderItem item = new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"));
        order.addItem(item);
        when(orderItemRepository.findByMerchantId("merchant-1")).thenReturn(List.of(item));

        var items = orderService.listMerchantOrderItems(MERCHANT);

        assertEquals(1, items.size());
        assertEquals(new BigDecimal("7.00"), items.get(0).lineTotal());
        assertEquals(OrderStatus.EN_ATTENTE, items.get(0).orderStatus());
    }

    @Test
    @DisplayName("Vue commerçant demandée par un client : interdit")
    void listMerchantOrderItemsByClientIsForbidden() {
        assertThrows(ForbiddenException.class, () -> orderService.listMerchantOrderItems(CLIENT));
    }
}
