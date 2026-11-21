package com.shoploc.shoplocorderservice;

import com.shoploc.shoplocorderservice.catalog.FakeCatalogConfiguration;
import com.shoploc.shoplocorderservice.catalog.FakeProductCatalogClient;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours du panier de bout en bout : HTTP -> controller -> service -> vrai PostgreSQL (Testcontainers),
 * avec le faux catalogue en mémoire (FakeProductCatalogClient).
 */
@Import({TestcontainersConfiguration.class, FakeCatalogConfiguration.class})
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CartFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FakeProductCatalogClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        orderRepository.deleteAll();
        catalog.reset();
    }

    private ResultActions asClient(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"));
    }

    private ResultActions addToCart(long productId, int quantity) throws Exception {
        return asClient(post("/api/order/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": " + productId + ", \"quantity\": " + quantity + "}"));
    }

    @Test
    @DisplayName("Ajouter au panier, modifier, valider : la commande passe EN_ATTENTE et le stock baisse")
    void cartToPendingOrder() throws Exception {
        addToCart(1, 1).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PANIER"));
        addToCart(1, 1).andExpect(jsonPath("$.items[0].quantity").value(2));   // même article : 1 + 1
        addToCart(3, 1).andExpect(jsonPath("$.items.length()").value(2));
        asClient(put("/api/order/cart/items/3").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(20.6));              // 2 x 3.50 + 2 x 6.80

        // tant que le panier n'est pas validé : stock intact et invisible pour le commerçant
        assertEquals(100, catalog.findProduct(1L).orElseThrow().stock());
        mockMvc.perform(get("/api/order/merchant/items").header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(jsonPath("$.length()").value(0));

        asClient(post("/api/order/cart/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupSlot\": \"2099-06-01T10:30:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.pickupSlot").value("2099-06-01T10:30:00"));

        assertEquals(98, catalog.findProduct(1L).orElseThrow().stock());
        assertEquals(18, catalog.findProduct(3L).orElseThrow().stock());
        assertEquals(OrderStatus.EN_ATTENTE, orderRepository.findAll().get(0).getStatus());

        // le commerçant voit maintenant sa ligne, et le client a un nouveau panier vide
        mockMvc.perform(get("/api/order/merchant/items").header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(jsonPath("$.length()").value(1));
        asClient(get("/api/order/cart")).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("Retirer le dernier article supprime le panier ; valider un panier vide donne 400")
    void removeLastItemThenValidateEmptyCart() throws Exception {
        addToCart(1, 1).andExpect(status().isOk());

        asClient(delete("/api/order/cart/items/1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        assertTrue(orderRepository.findAll().isEmpty());

        asClient(post("/api/order/cart/validate")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ajouter plus que le stock : 409 ; article inactif : 422")
    void addToCartChecksCatalog() throws Exception {
        addToCart(2, 6).andExpect(status().isConflict());     // croissant : stock 5
        addToCart(4, 1).andExpect(status().is(422));          // confiture inactive
        assertTrue(orderRepository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Le panier apparaît dans Mes commandes avec le statut PANIER")
    void cartAppearsInMyOrders() throws Exception {
        addToCart(1, 2).andExpect(status().isOk());

        asClient(get("/api/order"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PANIER"));
    }
}
