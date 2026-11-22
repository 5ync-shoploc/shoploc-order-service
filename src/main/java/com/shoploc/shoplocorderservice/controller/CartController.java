package com.shoploc.shoplocorderservice.controller;

import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.dto.UpdateCartItemRequest;
import com.shoploc.shoplocorderservice.dto.ValidateCartRequest;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.CurrentUserHeaders;
import com.shoploc.shoplocorderservice.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Panier", description = "Panier du client connecté (rôle CLIENT). Le panier est une commande au statut PANIER.")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** Mon panier (vide s'il n'existe pas encore). */
    @Operation(summary = "Mon panier", description = "Renvoie un panier vide (id null) s'il n'existe pas encore.")
    @CurrentUserHeaders
    @GetMapping
    public OrderResponse getCart(@Parameter(hidden = true) CurrentUser user) {
        return cartService.getCart(user);
    }

    /** Ajouter un article au panier. */
    @Operation(summary = "Ajouter un article", description = "Les quantités s'additionnent si l'article est déjà dans le panier.")
    @ApiResponse(responseCode = "422", description = "Article inconnu ou indisponible")
    @CurrentUserHeaders
    @PostMapping("/items")
    public OrderResponse addItem(@Parameter(hidden = true) CurrentUser user,
                                  @Valid @RequestBody OrderItemRequest request) {
        return cartService.addItem(user, request);
    }

    /** Changer la quantité d'un article du panier. */
    @Operation(summary = "Changer la quantité d'un article")
    @ApiResponse(responseCode = "404", description = "Article absent du panier")
    @CurrentUserHeaders
    @PutMapping("/items/{productId}")
    public OrderResponse updateItem(@Parameter(hidden = true) CurrentUser user, @PathVariable Long productId,
                                    @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(user, productId, request);
    }

    /** Retirer un article du panier. */
    @Operation(summary = "Retirer un article du panier")
    @ApiResponse(responseCode = "404", description = "Article absent du panier")
    @CurrentUserHeaders
    @DeleteMapping("/items/{productId}")
    public OrderResponse removeItem(@Parameter(hidden = true) CurrentUser user, @PathVariable Long productId) {
        return cartService.removeItem(user, productId);
    }

    /** Valider le panier : il devient une commande EN_ATTENTE. */
    @Operation(summary = "Valider le panier",
            description = "Le panier devient une commande EN_ATTENTE : les prix sont figés et le stock décrémenté auprès de catalog-service.")
    @ApiResponse(responseCode = "400", description = "Panier vide")
    @ApiResponse(responseCode = "409", description = "Stock insuffisant pour un des articles")
    @ApiResponse(responseCode = "503", description = "catalog-service injoignable")
    @CurrentUserHeaders
    @PostMapping("/validate")
    public OrderResponse validate(@Parameter(hidden = true) CurrentUser user,
                                   @Valid @RequestBody(required = false) ValidateCartRequest request) {
        return cartService.validate(user, request);
    }
}
