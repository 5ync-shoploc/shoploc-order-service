package com.shoploc.shoplocorderservice.catalog;

import com.shoploc.shoplocorderservice.exception.CatalogUnavailableException;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Vérifie les appels HTTP vers le catalogue avec un faux serveur (MockRestServiceServer). */
class HttpProductCatalogClientTest {

    private static final String BASE_URL = "http://catalog";

    private MockRestServiceServer server;
    private HttpProductCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpProductCatalogClient(builder, BASE_URL);
    }

    // ---------- GET /api/products/{id}/internal ----------

    @Test
    @DisplayName("Produit trouvé : prix, stock et commerçant lus depuis le catalogue")
    void findProductReturnsProductInfo() {
        server.expect(requestTo(BASE_URL + "/api/products/1/internal"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":1,"name":"Pain","price":3.50,"active":true,"stock":100,"merchantId":"merchant-1"}
                        """, MediaType.APPLICATION_JSON));

        ProductInfo product = client.findProduct(1L).orElseThrow();

        assertEquals("Pain", product.name());
        assertEquals(new BigDecimal("3.50"), product.price());
        assertTrue(product.active());
        assertEquals(100, product.stock());
        assertEquals("merchant-1", product.merchantId());
        server.verify();
    }

    @Test
    @DisplayName("Produit inactif : renvoyé comme non actif (l'order-service répond 422)")
    void findProductInactiveProduct() {
        server.expect(requestTo(BASE_URL + "/api/products/4/internal"))
                .andRespond(withSuccess("""
                        {"id":4,"name":"Confiture","price":4.00,"active":false,"stock":50,"merchantId":"merchant-2"}
                        """, MediaType.APPLICATION_JSON));

        assertFalse(client.findProduct(4L).orElseThrow().active());
    }

    @Test
    @DisplayName("Produit sans commerçant : traité comme non commandable")
    void findProductWithoutMerchantIsNotOrderable() {
        server.expect(requestTo(BASE_URL + "/api/products/5/internal"))
                .andRespond(withSuccess("""
                        {"id":5,"name":"Sans commerce","price":1.00,"active":true,"stock":null,"merchantId":null}
                        """, MediaType.APPLICATION_JSON));

        ProductInfo product = client.findProduct(5L).orElseThrow();
        assertFalse(product.active());
        assertEquals(0, product.stock());
    }

    @Test
    @DisplayName("Produit inconnu (404) : résultat vide")
    void findProductUnknownReturnsEmpty() {
        server.expect(requestTo(BASE_URL + "/api/products/999/internal")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(Optional.empty(), client.findProduct(999L));
    }

    @Test
    @DisplayName("Catalogue en erreur (500) : CatalogUnavailableException")
    void findProductServerErrorThrowsUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/products/1/internal")).andRespond(withServerError());

        assertThrows(CatalogUnavailableException.class, () -> client.findProduct(1L));
    }

    @Test
    @DisplayName("Catalogue injoignable : CatalogUnavailableException")
    void findProductConnectionErrorThrowsUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/products/1/internal")).andRespond(withException(new IOException("Connection refused")));

        assertThrows(CatalogUnavailableException.class, () -> client.findProduct(1L));
    }

    // ---------- POST /api/products/stock/decrement ----------

    @Test
    @DisplayName("Décrément accepté (204) : envoie tous les articles en une requête")
    void decrementStockSendsAllItems() {
        server.expect(requestTo(BASE_URL + "/api/products/stock/decrement"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"items":[{"productId":1,"quantity":2},{"productId":3,"quantity":1}]}
                        """))
                .andRespond(withNoContent());

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(1L, 2);
        quantities.put(3L, 1);
        client.decrementStock(quantities);

        server.verify();
    }

    @Test
    @DisplayName("Stock insuffisant (409) : InsufficientStockException avec le message du catalogue")
    void decrementStockConflictThrowsInsufficientStock() {
        server.expect(requestTo(BASE_URL + "/api/products/stock/decrement"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"status":409,"detail":"Stock insuffisant pour le produit 2"}
                                """));

        InsufficientStockException e = assertThrows(InsufficientStockException.class, () -> client.decrementStock(Map.of(2L, 6)));
        assertEquals("Stock insuffisant pour le produit 2", e.getMessage());
    }

    @Test
    @DisplayName("Produit inconnu au décrément (404) : ProductNotFoundException")
    void decrementStockNotFoundThrowsProductNotFound() {
        server.expect(requestTo(BASE_URL + "/api/products/stock/decrement")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        ProductNotFoundException e = assertThrows(ProductNotFoundException.class, () -> client.decrementStock(Map.of(999L, 1)));
        assertEquals("Unknown product", e.getMessage());
    }

    @Test
    @DisplayName("Catalogue injoignable au décrément : CatalogUnavailableException")
    void decrementStockConnectionErrorThrowsUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/products/stock/decrement")).andRespond(withException(new IOException("Connection refused")));

        assertThrows(CatalogUnavailableException.class, () -> client.decrementStock(Map.of(1L, 1)));
    }

    // ---------- configuration ----------

    @Test
    @DisplayName("Mode http : la configuration crée un client HTTP vers le catalogue")
    void configCreatesHttpClient() {
        ProductCatalogClient httpClient = new CatalogClientConfig().httpProductCatalogClient("http://localhost:8081");

        assertInstanceOf(HttpProductCatalogClient.class, httpClient);
    }
}
