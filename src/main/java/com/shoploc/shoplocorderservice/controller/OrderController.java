package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** US1 : passer une commande (rôle CLIENT). */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(CurrentUser user,
                                                     @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(user, request);
        return ResponseEntity
                .created(URI.create("/api/order/" + created.id()))
                .body(created);
    }

    /** US2 : mes commandes (rôle CLIENT). */
    @GetMapping
    public List<OrderResponse> listMyOrders(CurrentUser user) {
        return orderService.listMyOrders(user);
    }

    /** US2 : détail d'une de mes commandes (rôle CLIENT). */
    @GetMapping("/{orderId}")
    public OrderResponse getMyOrder(CurrentUser user, @PathVariable Long orderId) {
        return orderService.getMyOrder(user, orderId);
    }

}
