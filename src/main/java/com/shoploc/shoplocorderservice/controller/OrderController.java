package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.MerchantOrderDetailResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderItemResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderSummaryResponse;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.CurrentUserHeaders;
import com.shoploc.shoplocorderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@Tag(name = "Commandes", description = "Commandes, vue client (CLIENT) et vue commerçant (COMMERCANT).")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** US1 : passer une commande (rôle CLIENT). */
    @Operation(summary = "Passer une commande", description = "Articles existants, actifs et en stock suffisant requis ; le stock est décrémenté.")
    @ApiResponse(responseCode = "201", description = "Commande créée")
    @ApiResponse(responseCode = "422", description = "Article inconnu ou indisponible")
    @ApiResponse(responseCode = "409", description = "Stock insuffisant")
    @CurrentUserHeaders
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Parameter(hidden = true) CurrentUser user,
                                                     @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(user, request);
        return ResponseEntity
                .created(URI.create("/api/order/" + created.id()))
                .body(created);
    }

    /** US2 : mes commandes (rôle CLIENT). */
    @Operation(summary = "Mes commandes", description = "Uniquement les commandes du client connecté.")
    @CurrentUserHeaders
    @GetMapping
    public List<OrderResponse> listMyOrders(@Parameter(hidden = true) CurrentUser user) {
        return orderService.listMyOrders(user);
    }

    /** US2 : détail d'une de mes commandes (rôle CLIENT). */
    @Operation(summary = "Détail d'une de mes commandes")
    @ApiResponse(responseCode = "404", description = "Commande introuvable ou appartenant à un autre client")
    @CurrentUserHeaders
    @GetMapping("/{orderId}")
    public OrderResponse getMyOrder(@Parameter(hidden = true) CurrentUser user, @PathVariable Long orderId) {
        return orderService.getMyOrder(user, orderId);
    }

    /** Le client supprime une commande de sa liste : elle est annulée (rôle CLIENT). */
    @Operation(summary = "Annuler une commande", description = "Possible seulement si elle est encore EN_ATTENTE.")
    @ApiResponse(responseCode = "404", description = "Commande introuvable ou appartenant à un autre client")
    @ApiResponse(responseCode = "409", description = "Commande déjà prise en charge, non annulable")
    @CurrentUserHeaders
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelMyOrder(@Parameter(hidden = true) CurrentUser user, @PathVariable Long orderId) {
        orderService.cancelMyOrder(user, orderId);
        return ResponseEntity.noContent().build();
    }

    /** US3 : commandes contenant mes articles, une ligne par commande (rôle COMMERCANT). */
    @Operation(summary = "Commandes contenant mes articles", description = "Vue commerçant : une ligne par commande, la plus récente d'abord.")
    @CurrentUserHeaders
    @GetMapping("/merchant/orders")
    public List<MerchantOrderSummaryResponse> listMerchantOrders(@Parameter(hidden = true) CurrentUser user) {
        return orderService.listMerchantOrders(user);
    }

    /** US3 : contenu d'une commande, limité à mes articles (rôle COMMERCANT). */
    @Operation(summary = "Détail d'une commande (vue commerçant)", description = "Limité aux lignes qui concernent le commerçant connecté.")
    @ApiResponse(responseCode = "404", description = "Commande introuvable ou ne contenant aucun article du commerçant")
    @CurrentUserHeaders
    @GetMapping("/merchant/orders/{orderId}")
    public MerchantOrderDetailResponse getMerchantOrder(@Parameter(hidden = true) CurrentUser user, @PathVariable Long orderId) {
        return orderService.getMerchantOrder(user, orderId);
    }

    @Operation(summary = "Toutes mes lignes de commande", description = "Vue commerçant : une entrée par ligne (et non par commande).")
    @CurrentUserHeaders
    @GetMapping("/merchant/items")
    public List<MerchantOrderItemResponse> listMerchantOrderItems(@Parameter(hidden = true) CurrentUser user) {
        return orderService.listMerchantOrderItems(user);
    }
}
