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

    @Test
    @DisplayName("Le client supprime sa commande : elle disparaît de sa liste, le commerçant la voit ANNULEE")
    void clientCancelsOrderMerchantSeesItCancelled() throws Exception {
        addToCart(1, 2).andExpect(status().isOk());   // Pain, merchant-1
        addToCart(3, 1).andExpect(status().isOk());   // Fromage, merchant-2
        asClient(post("/api/order/cart/validate")).andExpect(status().isOk());
        int orderId = (int) (long) orderRepository.findAll().get(0).getId();

        // le commerçant voit la commande avec le client, et dans le détail seulement son article
        mockMvc.perform(get("/api/order/merchant/orders").header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].clientId").value("client-1"))
                .andExpect(jsonPath("$[0].itemCount").value(2))
                .andExpect(jsonPath("$[0].totalAmount").value(7.0));
        mockMvc.perform(get("/api/order/merchant/orders/" + orderId).header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Pain de campagne"));

        asClient(delete("/api/order/" + orderId)).andExpect(status().isNoContent());

        // côté client : la commande n'est plus listée ; une seconde suppression donne 404
        asClient(get("/api/order")).andExpect(jsonPath("$.length()").value(0));
        asClient(delete("/api/order/" + orderId)).andExpect(status().isNotFound());

        // en base : la commande est toujours là, annulée ; le commerçant la voit ANNULEE
        assertEquals(OrderStatus.ANNULEE, orderRepository.findAll().get(0).getStatus());
        mockMvc.perform(get("/api/order/merchant/orders").header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(jsonPath("$[0].status").value("ANNULEE"));
    }

    @Test
    @DisplayName("Le nom du client (en-tête X-User-Name) est enregistré à la validation et visible par le commerçant")
    void clientNameIsStoredAndShownToMerchant() throws Exception {
        addToCart(1, 1).andExpect(status().isOk());
        mockMvc.perform(post("/api/order/cart/validate")
                        .header("X-User-Id", "client-1").header("X-User-Role", "CLIENT")
                        .header("X-User-Name", "H%C3%A9l%C3%A8ne%20Dupr%C3%A9"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/order/merchant/orders").header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(jsonPath("$[0].clientName").value("Hélène Dupré"));
    }
}
