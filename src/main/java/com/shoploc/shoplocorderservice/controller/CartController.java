package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.dto.UpdateCartItemRequest;
import com.shoploc.shoplocorderservice.dto.ValidateCartRequest;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panier du client connecté (rôle CLIENT). */
@RestController
@RequestMapping("/api/order/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** Mon panier (vide s'il n'existe pas encore). */
    @GetMapping
    public OrderResponse getCart(CurrentUser user) {
        return cartService.getCart(user);
    }

    /** Ajouter un article au panier. */
    @PostMapping("/items")
    public OrderResponse addItem(CurrentUser user, @Valid @RequestBody OrderItemRequest request) {
        return cartService.addItem(user, request);
    }

    /** Changer la quantité d'un article du panier. */
    @PutMapping("/items/{productId}")
    public OrderResponse updateItem(CurrentUser user, @PathVariable Long productId,
                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(user, productId, request);
    }

    /** Retirer un article du panier. */
    @DeleteMapping("/items/{productId}")
    public OrderResponse removeItem(CurrentUser user, @PathVariable Long productId) {
        return cartService.removeItem(user, productId);
    }

    /** Valider le panier : il devient une commande EN_ATTENTE. */
    @PostMapping("/validate")
    public OrderResponse validate(CurrentUser user, @Valid @RequestBody(required = false) ValidateCartRequest request) {
        return cartService.validate(user, request);
    }
}
