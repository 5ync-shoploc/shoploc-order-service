package com.shoploc.shoplocorderservice.service;

import com.shoploc.shoplocorderservice.catalog.ProductCatalogClient;
import com.shoploc.shoplocorderservice.catalog.ProductInfo;
import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.MerchantOrderDetailResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderSummaryResponse;
import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.OrderNotCancellableException;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("Vue commerçant : les paniers (pas encore commandés) ne sont pas visibles")
    void listMerchantOrderItemsHidesCarts() {
        Order cart = new Order();
        cart.setClientId("client-1");
        cart.setStatus(OrderStatus.PANIER);
        OrderItem item = new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"));
        cart.addItem(item);
        when(orderItemRepository.findByMerchantId("merchant-1")).thenReturn(List.of(item));

        assertEquals(0, orderService.listMerchantOrderItems(MERCHANT).size());
    }

    @Test
    @DisplayName("Vue commerçant demandée par un client : interdit")
    void listMerchantOrderItemsByClientIsForbidden() {
        assertThrows(ForbiddenException.class, () -> orderService.listMerchantOrderItems(CLIENT));
    }

    // ---------- Suppression d'une commande par le client ----------

    private static Order order(long id, String clientId, OrderStatus status) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setClientId(clientId);
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("Suppression : la commande EN_ATTENTE passe ANNULEE et reste en base")
    void cancelMyOrderSetsStatusCancelled() {
        Order order = order(5L, "client-1", OrderStatus.EN_ATTENTE);
        when(orderRepository.findByIdAndClientId(5L, "client-1")).thenReturn(Optional.of(order));

        orderService.cancelMyOrder(CLIENT, 5L);

        assertEquals(OrderStatus.ANNULEE, order.getStatus());
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    @DisplayName("Suppression d'une commande déjà prête : OrderNotCancellableException")
    void cancelMyOrderWhenNotPendingThrowsNotCancellable() {
        Order order = order(5L, "client-1", OrderStatus.PRETE);
        when(orderRepository.findByIdAndClientId(5L, "client-1")).thenReturn(Optional.of(order));

        assertThrows(OrderNotCancellableException.class, () -> orderService.cancelMyOrder(CLIENT, 5L));
        assertEquals(OrderStatus.PRETE, order.getStatus());
    }

    @Test
    @DisplayName("Suppression d'une commande déjà annulée ou d'un panier : OrderNotFoundException")
    void cancelMyOrderWhenAlreadyCancelledOrCartThrowsNotFound() {
        when(orderRepository.findByIdAndClientId(5L, "client-1"))
                .thenReturn(Optional.of(order(5L, "client-1", OrderStatus.ANNULEE)));
        when(orderRepository.findByIdAndClientId(6L, "client-1"))
                .thenReturn(Optional.of(order(6L, "client-1", OrderStatus.PANIER)));

        assertThrows(OrderNotFoundException.class, () -> orderService.cancelMyOrder(CLIENT, 5L));
        assertThrows(OrderNotFoundException.class, () -> orderService.cancelMyOrder(CLIENT, 6L));
    }

    @Test
    @DisplayName("Suppression de la commande d'un autre client : OrderNotFoundException")
    void cancelMyOrderOfAnotherClientThrowsNotFound() {
        when(orderRepository.findByIdAndClientId(5L, "client-1")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.cancelMyOrder(CLIENT, 5L));
    }

    @Test
    @DisplayName("Suppression par un commerçant : interdit")
    void cancelMyOrderByMerchantIsForbidden() {
        assertThrows(ForbiddenException.class, () -> orderService.cancelMyOrder(MERCHANT, 5L));
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Mes commandes : les commandes annulées n'apparaissent plus")
    void listMyOrdersHidesCancelledOrders() {
        when(orderRepository.findByClientIdOrderByIdDesc("client-1")).thenReturn(List.of(
                order(2L, "client-1", OrderStatus.ANNULEE),
                order(1L, "client-1", OrderStatus.EN_ATTENTE)));

        List<OrderResponse> orders = orderService.listMyOrders(CLIENT);

        assertEquals(1, orders.size());
        assertEquals(1L, orders.get(0).id());
    }

    // ---------- Vue commerçant : liste et contenu des commandes ----------

    @Test
    @DisplayName("Liste commerçant : une ligne par commande, ses articles seulement, plus récente d'abord")
    void listMerchantOrdersGroupsItemsByOrder() {
        Order older = order(1L, "client-1", OrderStatus.EN_ATTENTE);
        OrderItem bread = new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50"));
        OrderItem croissant = new OrderItem(2L, "Croissant", "merchant-1", 3, new BigDecimal("1.20"));
        older.addItem(bread);
        older.addItem(croissant);
        older.addItem(new OrderItem(3L, "Fromage", "merchant-2", 1, new BigDecimal("6.80")));
        Order newer = order(2L, "client-2", OrderStatus.ANNULEE);
        OrderItem bread2 = new OrderItem(1L, "Pain", "merchant-1", 1, new BigDecimal("3.50"));
        newer.addItem(bread2);
        Order cart = order(3L, "client-1", OrderStatus.PANIER);
        OrderItem inCart = new OrderItem(1L, "Pain", "merchant-1", 1, new BigDecimal("3.50"));
        cart.addItem(inCart);
        when(orderItemRepository.findByMerchantId("merchant-1"))
                .thenReturn(List.of(bread, croissant, bread2, inCart));

        List<MerchantOrderSummaryResponse> orders = orderService.listMerchantOrders(MERCHANT);

        assertEquals(2, orders.size());
        assertEquals(2L, orders.get(0).orderId());
        assertEquals(OrderStatus.ANNULEE, orders.get(0).status());
        assertEquals("client-2", orders.get(0).clientId());
        assertEquals(1L, orders.get(1).orderId());
        assertEquals(5, orders.get(1).itemCount());
        assertEquals(new BigDecimal("10.60"), orders.get(1).totalAmount());
    }

    @Test
    @DisplayName("Liste commerçant demandée par un client : interdit")
    void listMerchantOrdersByClientIsForbidden() {
        assertThrows(ForbiddenException.class, () -> orderService.listMerchantOrders(CLIENT));
    }

    @Test
    @DisplayName("Contenu d'une commande côté commerçant : uniquement ses articles")
    void getMerchantOrderReturnsOnlyItemsOfCurrentMerchant() {
        Order order = order(1L, "client-1", OrderStatus.EN_ATTENTE);
        order.addItem(new OrderItem(1L, "Pain", "merchant-1", 2, new BigDecimal("3.50")));
        order.addItem(new OrderItem(3L, "Fromage", "merchant-2", 1, new BigDecimal("6.80")));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        MerchantOrderDetailResponse detail = orderService.getMerchantOrder(MERCHANT, 1L);

        assertEquals("client-1", detail.clientId());
        assertEquals(1, detail.items().size());
        assertEquals("Pain", detail.items().get(0).productName());
        assertEquals(new BigDecimal("7.00"), detail.totalAmount());
    }

    @Test
    @DisplayName("Contenu d'une commande sans article du commerçant : OrderNotFoundException")
    void getMerchantOrderWithoutOwnItemsThrowsNotFound() {
        Order order = order(1L, "client-1", OrderStatus.EN_ATTENTE);
        order.addItem(new OrderItem(3L, "Fromage", "merchant-2", 1, new BigDecimal("6.80")));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(OrderNotFoundException.class, () -> orderService.getMerchantOrder(MERCHANT, 1L));
    }

    @Test
    @DisplayName("Contenu d'un panier ou d'une commande inconnue côté commerçant : OrderNotFoundException")
    void getMerchantOrderOfCartOrUnknownThrowsNotFound() {
        Order cart = order(1L, "client-1", OrderStatus.PANIER);
        cart.addItem(new OrderItem(1L, "Pain", "merchant-1", 1, new BigDecimal("3.50")));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> orderService.getMerchantOrder(MERCHANT, 1L));
        assertThrows(OrderNotFoundException.class, () -> orderService.getMerchantOrder(MERCHANT, 9L));
    }

    @Test
    @DisplayName("Contenu d'une commande côté commerçant demandé par un client : interdit")
    void getMerchantOrderByClientIsForbidden() {
        assertThrows(ForbiddenException.class, () -> orderService.getMerchantOrder(CLIENT, 1L));
    }
}
