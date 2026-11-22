package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.MerchantOrderDetailResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderItemResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderSummaryResponse;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.CatalogUnavailableException;
import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.GlobalExceptionHandler;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.OrderNotCancellableException;
import com.shoploc.shoplocorderservice.exception.OrderNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.CurrentUserArgumentResolver;
import com.shoploc.shoplocorderservice.security.Role;
import com.shoploc.shoplocorderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private static final String VALID_BODY = """
            {"items": [{"productId": 1, "quantity": 2}]}
            """;
    private static final CurrentUser CLIENT = new CurrentUser("client-1", Role.CLIENT);

    private OrderService orderService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    private ResultActions postOrder(String userId, String role, String body) throws Exception {
        var request = post("/api/order").contentType(MediaType.APPLICATION_JSON).content(body);
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        if (role != null) {
            request.header("X-User-Role", role);
        }
        return mockMvc.perform(request);
    }

    private static OrderResponse sampleOrder() {
        return new OrderResponse(7L, "client-1", OrderStatus.EN_ATTENTE, new BigDecimal("7.00"),
                Instant.now(), null, null, List.of());
    }

    // ---------- POST /api/order ----------

    @Test
    @DisplayName("POST /api/order : 201 avec l'en-tête Location")
    void createOrderReturns201WithLocation() throws Exception {
        when(orderService.createOrder(any(CurrentUser.class), any(CreateOrderRequest.class))).thenReturn(sampleOrder());

        postOrder("client-1", "client", VALID_BODY) // le rôle est insensible à la casse
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/order/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"));
    }

    @Test
    @DisplayName("POST sans article : 400")
    void createOrderWithoutItemsReturns400() throws Exception {
        postOrder("client-1", "CLIENT", """
                {"items": []}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST avec une quantité à 0 : 400")
    void createOrderWithZeroQuantityReturns400() throws Exception {
        postOrder("client-1", "CLIENT", """
                {"items": [{"productId": 1, "quantity": 0}]}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST avec un créneau de retrait dans le passé : 400")
    void createOrderWithPastPickupSlotReturns400() throws Exception {
        postOrder("client-1", "CLIENT", """
                {"items": [{"productId": 1, "quantity": 1}], "pickupSlot": "2020-01-01T10:00:00"}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST sans identité : 401")
    void createOrderWithoutIdentityHeadersReturns401() throws Exception {
        postOrder(null, null, VALID_BODY).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST avec un rôle inconnu : 403")
    void createOrderWithUnknownRoleReturns403() throws Exception {
        postOrder("x", "ADMIN", VALID_BODY).andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("POST refusé par le service (mauvais rôle) : 403")
    void createOrderReturns403WhenServiceForbids() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ForbiddenException("Role CLIENT required"));
        postOrder("merchant-1", "COMMERCANT", VALID_BODY).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST avec un article inconnu : 422")
    void createOrderWithUnknownProductReturns422() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ProductNotFoundException(99L));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().is(422));
    }

    @Test
    @DisplayName("POST avec un article inactif : 422")
    void createOrderWithInactiveProductReturns422() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ProductUnavailableException(4L));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().is(422));
    }

    @Test
    @DisplayName("POST quand le catalogue est indisponible : 503")
    void createOrderWhenCatalogUnavailableReturns503() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new CatalogUnavailableException("Catalog service unavailable", null));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST avec un stock insuffisant : 409")
    void createOrderWithInsufficientStockReturns409() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new InsufficientStockException(2L, 6, 5));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().isConflict());
    }

    // ---------- GET ----------

    @Test
    @DisplayName("GET /api/order : 200")
    void listMyOrdersReturns200() throws Exception {
        when(orderService.listMyOrders(CLIENT)).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/order").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/order/{id} : 200")
    void getMyOrderReturns200() throws Exception {
        when(orderService.getMyOrder(CLIENT, 7L)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/order/7").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("GET /api/order/{id} d'un autre client : 404")
    void getMyOrderOfAnotherClientReturns404() throws Exception {
        when(orderService.getMyOrder(CLIENT, 8L)).thenThrow(new OrderNotFoundException(8L));

        mockMvc.perform(get("/api/order/8").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/order/merchant/items : 200")
    void listMerchantOrderItemsReturns200() throws Exception {
        CurrentUser merchant = new CurrentUser("merchant-1", Role.COMMERCANT);
        when(orderService.listMerchantOrderItems(merchant)).thenReturn(List.of(
                new MerchantOrderItemResponse(7L, OrderStatus.EN_ATTENTE, Instant.now(), 1L, "Pain", 2,
                        new BigDecimal("3.50"), new BigDecimal("7.00"))));

        mockMvc.perform(get("/api/order/merchant/items")
                        .header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Pain"));
    }

    // ---------- DELETE /api/order/{id} (suppression par le client) ----------

    @Test
    @DisplayName("DELETE /api/order/{id} : 204")
    void cancelMyOrderReturns204() throws Exception {
        mockMvc.perform(delete("/api/order/7").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isNoContent());

        verify(orderService).cancelMyOrder(CLIENT, 7L);
    }

    @Test
    @DisplayName("DELETE /api/order/{id} d'une commande déjà prête : 409")
    void cancelMyOrderNotCancellableReturns409() throws Exception {
        doThrow(new OrderNotCancellableException(7L, OrderStatus.PRETE)).when(orderService).cancelMyOrder(CLIENT, 7L);

        mockMvc.perform(delete("/api/order/7").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/order/{id} d'une commande d'un autre client : 404")
    void cancelMyOrderOfAnotherClientReturns404() throws Exception {
        doThrow(new OrderNotFoundException(8L)).when(orderService).cancelMyOrder(CLIENT, 8L);

        mockMvc.perform(delete("/api/order/8").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound());
    }

    // ---------- GET /api/order/merchant/orders ----------

    @Test
    @DisplayName("GET /api/order/merchant/orders : 200")
    void listMerchantOrdersReturns200() throws Exception {
        CurrentUser merchant = new CurrentUser("merchant-1", Role.COMMERCANT);
        when(orderService.listMerchantOrders(merchant)).thenReturn(List.of(
                new MerchantOrderSummaryResponse(7L, Instant.now(), OrderStatus.ANNULEE, "client-1", "Obed Dupont", 2,
                        new BigDecimal("7.00"))));

        mockMvc.perform(get("/api/order/merchant/orders")
                        .header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(7))
                .andExpect(jsonPath("$[0].clientId").value("client-1"))
                .andExpect(jsonPath("$[0].clientName").value("Obed Dupont"))
                .andExpect(jsonPath("$[0].status").value("ANNULEE"));
    }

    @Test
    @DisplayName("GET /api/order/merchant/orders/{id} : 200")
    void getMerchantOrderReturns200() throws Exception {
        CurrentUser merchant = new CurrentUser("merchant-1", Role.COMMERCANT);
        when(orderService.getMerchantOrder(merchant, 7L)).thenReturn(new MerchantOrderDetailResponse(
                7L, Instant.now(), OrderStatus.EN_ATTENTE, "client-1", "Obed Dupont", List.of(), new BigDecimal("7.00")));

        mockMvc.perform(get("/api/order/merchant/orders/7")
                        .header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(7))
                .andExpect(jsonPath("$.totalAmount").value(7.00));
    }

    @Test
    @DisplayName("GET /api/order/merchant/orders/{id} sans article du commerçant : 404")
    void getMerchantOrderWithoutOwnItemsReturns404() throws Exception {
        CurrentUser merchant = new CurrentUser("merchant-1", Role.COMMERCANT);
        when(orderService.getMerchantOrder(merchant, 8L)).thenThrow(new OrderNotFoundException(8L));

        mockMvc.perform(get("/api/order/merchant/orders/8")
                        .header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(status().isNotFound());
    }
}
