package com.shoploc.shoplocorderservice.service;

import com.shoploc.shoplocorderservice.catalog.ProductCatalogClient;
import com.shoploc.shoplocorderservice.catalog.ProductInfo;
import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.MerchantOrderItemResponse;
import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.OrderNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductNotFoundException;
import com.shoploc.shoplocorderservice.exception.ProductUnavailableException;
import com.shoploc.shoplocorderservice.repository.OrderItemRepository;
import com.shoploc.shoplocorderservice.repository.OrderRepository;
import com.shoploc.shoplocorderservice.security.CurrentUser;
import com.shoploc.shoplocorderservice.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductCatalogClient productCatalogClient;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        ProductCatalogClient productCatalogClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
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

    /** US2 : un client voit la liste de ses commandes. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listMyOrders(CurrentUser user) {
        user.requireRole(Role.CLIENT);
        return orderRepository.findByClientIdOrderByIdDesc(user.id()).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /** US2 : un client voit le détail d'une de ses commandes (404 si elle n'est pas à lui). */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(CurrentUser user, Long orderId) {
        user.requireRole(Role.CLIENT);
        return orderRepository.findByIdAndClientId(orderId, user.id())
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** US3 : un commerçant voit les lignes de commande qui concernent ses articles. */
    @Transactional(readOnly = true)
    public List<MerchantOrderItemResponse> listMerchantOrderItems(CurrentUser user) {
        user.requireRole(Role.COMMERCANT);
        return orderItemRepository.findByMerchantIdOrderByIdDesc(user.id()).stream()
                .map(MerchantOrderItemResponse::from)
                .toList();
    }
}
