package com.shoploc.shoplocorderservice.service;

import com.shoploc.shoplocorderservice.catalog.ProductCatalogClient;
import com.shoploc.shoplocorderservice.catalog.ProductInfo;
import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.repository.OrderRepository;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;

    public OrderService(OrderRepository orderRepository, ProductCatalogClient productCatalogClient) {
        this.orderRepository = orderRepository;
        this.productCatalogClient = productCatalogClient;
    }

    /** US1 : un client passe une commande. */
    @Transactional
    public OrderResponse createOrder(CurrentUser user, CreateOrderRequest request) {
        user.requireRole(Role.CLIENT);

        // Si le même produit apparaît deux fois, on additionne les quantités
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Order order = new Order();
        order.setClientId(user.id());
        order.setStatus(OrderStatus.PENDING);

        quantities.forEach((productId, quantity) -> {
            ProductInfo product = productCatalogClient.findProduct(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            if (!product.active()) {
                throw new ProductUnavailableException(productId);
            }
            if (product.stock() < quantity) {
                throw new InsufficientStockException(productId, quantity, product.stock());
            }
            // Le prix est copié ("figé") dans la commande
            order.addItem(new OrderItem(product.id(), product.name(), product.merchantId(), quantity, product.price()));
        });

        order.setTotalAmount(order.getItems().stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        Order saved = orderRepository.save(order);

        // Décrément du stock en dernier : s'il échoue, la transaction annule la commande
        productCatalogClient.decrementStock(quantities);

        return OrderResponse.from(saved);
    }

}
