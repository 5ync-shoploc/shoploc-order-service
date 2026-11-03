package com.shoploc.shoplocorderservice;

import com.jayway.jsonpath.JsonPath;
import com.shoploc.shoplocorderservice.catalog.InMemoryProductCatalogClient;
import com.shoploc.shoplocorderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de bout en bout : HTTP -> controller -> service -> vrai PostgreSQL (Testcontainers).
 * Le catalogue et l'identité sont simulés (InMemoryProductCatalogClient, en-têtes X-User-Id / X-User-Role).
 * ddl-auto=create-drop : Hibernate crée les tables pour les tests
 * (en temps normal, c'est le Config Server qui fixe ce réglage).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OrderFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InMemoryProductCatalogClient catalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        orderRepository.deleteAll();
        catalog.reset();
    }

    private ResultActions postOrder(String userId, String role, String itemsJson) throws Exception {
        return mockMvc.perform(post("/api/order")
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\": " + itemsJson + "}"));
    }

    private long createOrder(String clientId, String itemsJson) throws Exception {
        String body = postOrder(clientId, "CLIENT", itemsJson)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ---------- US1 ----------

    @Test
    void client_createsOrder_pricesFrozen_statusPending_stockDecremented() throws Exception {
        postOrder("client-1", "CLIENT", """
                [{"productId": 1, "quantity": 2}, {"productId": 3, "quantity": 1}]
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientId").value("client-1"))
                .andExpect(jsonPath("$.totalAmount").value(13.8))   // 2 x 3.50 + 1 x 6.80
                .andExpect(jsonPath("$.items[0].unitPrice").value(3.5))
                .andExpect(jsonPath("$.items[0].merchantId").value("merchant-1"));

        assertEquals(98, catalog.findProduct(1L).orElseThrow().stock());
        assertEquals(19, catalog.findProduct(3L).orElseThrow().stock());
        assertEquals(1, orderRepository.findAll().size());
    }

    @Test
    void insufficientStock_returns409_andNothingIsSaved() throws Exception {
        postOrder("client-1", "CLIENT", """
                [{"productId": 1, "quantity": 1}, {"productId": 2, "quantity": 6}]
                """).andExpect(status().isConflict());

        assertEquals(0, orderRepository.findAll().size());
        assertEquals(100, catalog.findProduct(1L).orElseThrow().stock());
        assertEquals(5, catalog.findProduct(2L).orElseThrow().stock());
    }

    @Test
    void inactiveOrUnknownProduct_returns422() throws Exception {
        postOrder("client-1", "CLIENT", "[{\"productId\": 4, \"quantity\": 1}]").andExpect(status().is(422));
        postOrder("client-1", "CLIENT", "[{\"productId\": 999, \"quantity\": 1}]").andExpect(status().is(422));
        assertEquals(0, orderRepository.findAll().size());
    }

    @Test
    void merchantCannotOrder_403_andAnonymousGets401() throws Exception {
        postOrder("merchant-1", "COMMERCANT", "[{\"productId\": 1, \"quantity\": 1}]")
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/order").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": 1, \"quantity\": 1}]}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- US2 ----------

    @Test
    void client_seesOnlyOwnOrders() throws Exception {
        long mine = createOrder("client-1", "[{\"productId\": 1, \"quantity\": 1}]");
        long other = createOrder("client-2", "[{\"productId\": 3, \"quantity\": 1}]");

        mockMvc.perform(get("/api/order").header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine));

        mockMvc.perform(get("/api/order/" + mine).header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Pain de campagne"));

        mockMvc.perform(get("/api/order/" + other).header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isNotFound());
    }

    // ---------- US3 ----------

    @Test
    void merchant_seesOnlyLinesOfOwnProducts() throws Exception {
        // une commande qui mélange les articles de merchant-1 (produit 1) et merchant-2 (produit 3)
        createOrder("client-1", "[{\"productId\": 1, \"quantity\": 2}, {\"productId\": 3, \"quantity\": 1}]");

        mockMvc.perform(get("/api/order/merchant/items")
                        .header("X-User-Id", "merchant-1").header("X-User-Role", "COMMERCANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].quantity").value(2));

        mockMvc.perform(get("/api/order/merchant/items")
                        .header("X-User-Id", "merchant-2").header("X-User-Role", "COMMERCANT"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productId").value(3));

        mockMvc.perform(get("/api/order/merchant/items")
                        .header("X-User-Id", "client-1").header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());
    }
}
