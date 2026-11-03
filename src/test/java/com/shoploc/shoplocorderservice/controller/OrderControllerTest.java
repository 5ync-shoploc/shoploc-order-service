package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.GlobalExceptionHandler;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.OrderNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.CurrentUserArgumentResolver;
import com.shoploc.shoplocorderservice.security.Role;
import com.shoploc.shoplocorderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
        return new OrderResponse(7L, "client-1", OrderStatus.PENDING, new BigDecimal("7.00"),
                Instant.now(), List.of());
    }

    // ---------- POST /api/order ----------

    @Test
    void createOrder_returns201WithLocation() throws Exception {
        when(orderService.createOrder(any(CurrentUser.class), any(CreateOrderRequest.class))).thenReturn(sampleOrder());

        postOrder("client-1", "client", VALID_BODY) // le rôle est insensible à la casse
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/order/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createOrder_withoutItems_returns400() throws Exception {
        postOrder("client-1", "CLIENT", """
                {"items": []}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_withZeroQuantity_returns400() throws Exception {
        postOrder("client-1", "CLIENT", """
                {"items": [{"productId": 1, "quantity": 0}]}
                """).andExpect(status().isBadRequest());
        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_withoutIdentityHeaders_returns401() throws Exception {
        postOrder(null, null, VALID_BODY).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_withUnknownRole_returns403() throws Exception {
        postOrder("x", "ADMIN", VALID_BODY).andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void createOrder_whenServiceForbids_returns403() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ForbiddenException("Role CLIENT required"));
        postOrder("merchant-1", "COMMERCANT", VALID_BODY).andExpect(status().isForbidden());
    }

    @Test
    void createOrder_unknownProduct_returns422() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ProductNotFoundException(99L));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().is(422));
    }

    @Test
    void createOrder_inactiveProduct_returns422() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new ProductUnavailableException(4L));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().is(422));
    }

    @Test
    void createOrder_insufficientStock_returns409() throws Exception {
        when(orderService.createOrder(any(), any())).thenThrow(new InsufficientStockException(2L, 6, 5));
        postOrder("client-1", "CLIENT", VALID_BODY).andExpect(status().isConflict());
    }

    // ---------- GET ----------

    @Test
    void listMyOrders_returns200() throws Exception {
        when(orderService.listMyOrders(CLIENT)).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/order").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getMyOrder_returns200() throws Exception {
        when(orderService.getMyOrder(CLIENT, 7L)).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/order/7").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void getMyOrder_notMine_returns404() throws Exception {
        when(orderService.getMyOrder(CLIENT, 8L)).thenThrow(new OrderNotFoundException(8L));

        mockMvc.perform(get("/api/order/8").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound());
    }
}
