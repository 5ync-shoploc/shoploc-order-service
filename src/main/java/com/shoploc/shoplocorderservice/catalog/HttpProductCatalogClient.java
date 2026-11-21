package com.shoploc.shoplocorderservice.catalog;

import com.shoploc.shoplocorderservice.exception.CatalogUnavailableException;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Accès au service catalogue (shoploc-catalog-service).
 * Endpoints internes, appelés directement (sans passer par la Gateway) :
 * - GET  /api/products/{id}/internal      -> 200 produit | 404 inconnu
 * - POST /api/products/stock/decrement    -> 204 | 409 stock insuffisant | 404 produit inconnu (tout ou rien)
 */
public class HttpProductCatalogClient implements ProductCatalogClient {

    private final RestClient restClient;

    public HttpProductCatalogClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Réponse de GET /api/products/{id}/internal. */
    public record InternalProduct(Long id, String name, BigDecimal price, Boolean active, Integer stock, String merchantId) {
    }

    /** Corps de POST /api/products/stock/decrement. */
    public record DecrementRequest(List<Item> items) {
        public record Item(Long productId, Integer quantity) {
        }
    }

    @Override
    public Optional<ProductInfo> findProduct(Long productId) {
        try {
            InternalProduct product = restClient.get()
                    .uri("/api/products/{id}/internal", productId)
                    .retrieve()
                    .body(InternalProduct.class);
            return Optional.ofNullable(product).map(HttpProductCatalogClient::toProductInfo);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void decrementStock(Map<Long, Integer> quantities) {
        List<DecrementRequest.Item> items = quantities.entrySet().stream()
                .map(entry -> new DecrementRequest.Item(entry.getKey(), entry.getValue()))
                .toList();
        try {
            restClient.post()
                    .uri("/api/products/stock/decrement")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DecrementRequest(items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new InsufficientStockException(detailOf(e, "Insufficient stock"));
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(detailOf(e, "Unknown product"));
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    /**
     * Un produit sans commerçant ne peut pas être commandé (la ligne de commande exige id_commercant) :
     * on le traite comme inactif, ce qui donne une erreur 422 côté commande.
     */
    private static ProductInfo toProductInfo(InternalProduct p) {
        boolean orderable = Boolean.TRUE.equals(p.active()) && p.merchantId() != null;
        int stock = p.stock() == null ? 0 : p.stock();
        return new ProductInfo(p.id(), p.name(), p.price(), orderable, stock, p.merchantId());
    }

    /** Message "detail" de la réponse d'erreur du catalogue (Problem Details), sinon le message par défaut. */
    private static String detailOf(RestClientResponseException e, String fallback) {
        try {
            ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
            if (problem != null && problem.getDetail() != null) {
                return problem.getDetail();
            }
        } catch (RuntimeException ignored) {
            // corps absent ou illisible : message par défaut
        }
        return fallback;
    }

    private static CatalogUnavailableException unavailable(RestClientException e) {
        return new CatalogUnavailableException("Catalog service unavailable: " + e.getMessage(), e);
    }
}
