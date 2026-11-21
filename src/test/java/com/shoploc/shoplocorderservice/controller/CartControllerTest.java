package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.dto.UpdateCartItemRequest;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.CartItemNotFoundException;
import com.shoploc.shoplocorderservice.exception.EmptyCartException;
import com.shoploc.shoplocorderservice.exception.GlobalExceptionHandler;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.CurrentUserArgumentResolver;
import com.shoploc.shoplocorderservice.security.Role;
import com.shoploc.shoplocorderservice.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerTest {

    private static final CurrentUser CLIENT = new CurrentUser("client-1", Role.CLIENT);
    private static final OrderResponse CART =
            new OrderResponse(5L, "client-1", OrderStatus.PANIER, new BigDecimal("7.00"), null, null, null, List.of());

    private CartService cartService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/order/cart : 200 avec le panier")
    void getCartReturns200() throws Exception {
        when(cartService.getCart(CLIENT)).thenReturn(CART);

        mockMvc.perform(get("/api/order/cart").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PANIER"));
    }

    @Test
    @DisplayName("POST /api/order/cart/items : 200 avec le panier mis à jour")
    void addItemReturns200() throws Exception {
        when(cartService.addItem(CLIENT, new OrderItemRequest(1L, 2))).thenReturn(CART);

        mockMvc.perform(post("/api/order/cart/items").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\": 1, \"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("POST /api/order/cart/items avec une quantité à 0 : 400")
    void addItemWithZeroQuantityReturns400() throws Exception {
        mockMvc.perform(post("/api/order/cart/items").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\": 1, \"quantity\": 0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("POST /api/order/cart/items sans identité : 401")
    void addItemWithoutIdentityReturns401() throws Exception {
        mockMvc.perform(post("/api/order/cart/items")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\": 1, \"quantity\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/order/cart/items/{id} : 200")
    void updateItemReturns200() throws Exception {
        when(cartService.updateItem(CLIENT, 1L, new UpdateCartItemRequest(3))).thenReturn(CART);

        mockMvc.perform(put("/api/order/cart/items/1").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 3}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/order/cart/items/{id} d'un article absent : 404")
    void removeMissingItemReturns404() throws Exception {
        when(cartService.removeItem(CLIENT, 9L)).thenThrow(new CartItemNotFoundException(9L));

        mockMvc.perform(delete("/api/order/cart/items/9").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/order/cart/validate avec un créneau : 200")
    void validateReturns200() throws Exception {
        when(cartService.validate(eq(CLIENT), any())).thenReturn(CART);

        mockMvc.perform(post("/api/order/cart/validate").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pickupSlot\": \"2099-01-01T10:00:00\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/order/cart/validate d'un panier vide (sans corps) : 400")
    void validateEmptyCartReturns400() throws Exception {
        when(cartService.validate(eq(CLIENT), isNull())).thenThrow(new EmptyCartException());

        mockMvc.perform(post("/api/order/cart/validate").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/order/cart/validate avec un créneau passé : 400")
    void validateWithPastPickupSlotReturns400() throws Exception {
        mockMvc.perform(post("/api/order/cart/validate").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pickupSlot\": \"2020-01-01T10:00:00\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cartService);
    }
}
