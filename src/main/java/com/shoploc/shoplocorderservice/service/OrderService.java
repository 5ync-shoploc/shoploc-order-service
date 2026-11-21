package com.shoploc.shoplocorderservice.service;

import com.shoploc.shoplocorderservice.catalog.ProductCatalogClient;
import com.shoploc.shoplocorderservice.catalog.ProductInfo;
import com.shoploc.shoplocorderservice.dto.CreateOrderRequest;
import com.shoploc.shoplocorderservice.dto.MerchantOrderDetailResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderItemResponse;
import com.shoploc.shoplocorderservice.dto.MerchantOrderSummaryResponse;
import com.shoploc.shoplocorderservice.dto.OrderItemResponse;
import com.shoploc.shoplocorderservice.dto.OrderItemRequest;
import com.shoploc.shoplocorderservice.dto.OrderResponse;
import com.shoploc.shoplocorderservice.entity.Order;
import com.shoploc.shoplocorderservice.entity.OrderItem;
import com.shoploc.shoplocorderservice.entity.OrderStatus;
import com.shoploc.shoplocorderservice.exception.InsufficientStockException;
import com.shoploc.shoplocorderservice.exception.OrderNotCancellableException;
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
import java.util.ArrayList;
import java.util.Comparator;
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
        order.setStatus(OrderStatus.EN_ATTENTE);
        order.setPickupSlot(request.pickupSlot());

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

    /** US2 : un client voit la liste de ses commandes (les commandes qu'il a supprimées n'y sont plus). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listMyOrders(CurrentUser user) {
        user.requireRole(Role.CLIENT);
        return orderRepository.findByClientIdOrderByIdDesc(user.id()).stream()
                .filter(order -> order.getStatus() != OrderStatus.ANNULEE)
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Le client supprime une commande de sa liste : elle passe au statut ANNULEE.
     * Elle reste en base et le commerçant la voit annulée. Possible seulement tant qu'elle est EN_ATTENTE.
     */
    @Transactional
    public void cancelMyOrder(CurrentUser user, Long orderId) {
        user.requireRole(Role.CLIENT);
        Order order = orderRepository.findByIdAndClientId(orderId, user.id())
                .filter(o -> o.getStatus() != OrderStatus.PANIER && o.getStatus() != OrderStatus.ANNULEE)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.EN_ATTENTE) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }
        order.setStatus(OrderStatus.ANNULEE);
        // TODO remettre le stock dans le catalogue quand le service catalogue proposera un endpoint pour cela
    }

    /** US2 : un client voit le détail d'une de ses commandes (404 si elle n'est pas à lui). */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(CurrentUser user, Long orderId) {
        user.requireRole(Role.CLIENT);
        return orderRepository.findByIdAndClientId(orderId, user.id())
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** US3 : un commerçant voit la liste des commandes qui contiennent ses articles (une ligne par commande). */
    @Transactional(readOnly = true)
    public List<MerchantOrderSummaryResponse> listMerchantOrders(CurrentUser user) {
        user.requireRole(Role.COMMERCANT);
        Map<Order, List<OrderItem>> itemsByOrder = merchantItemsByOrder(user.id());
        return itemsByOrder.entrySet().stream()
                .map(entry -> {
                    Order order = entry.getKey();
                    List<OrderItem> items = entry.getValue();
                    return new MerchantOrderSummaryResponse(
                            order.getId(),
                            order.getOrderDate(),
                            order.getStatus(),
                            order.getClientId(),
                            items.stream().mapToInt(OrderItem::getQuantity).sum(),
                            total(items));
                })
                .sorted(Comparator.comparing(MerchantOrderSummaryResponse::orderId).reversed())
                .toList();
    }

    /** US3 : un commerçant voit le contenu d'une commande (uniquement ses articles ; 404 sinon). */
    @Transactional(readOnly = true)
    public MerchantOrderDetailResponse getMerchantOrder(CurrentUser user, Long orderId) {
        user.requireRole(Role.COMMERCANT);
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getStatus() != OrderStatus.PANIER)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        List<OrderItem> items = order.getItems().stream()
                .filter(item -> user.id().equals(item.getMerchantId()))
                .toList();
        if (items.isEmpty()) {
            // la commande ne contient aucun article de ce commerçant : il ne doit pas la voir
            throw new OrderNotFoundException(orderId);
        }
        return new MerchantOrderDetailResponse(
                order.getId(),
                order.getOrderDate(),
                order.getStatus(),
                order.getClientId(),
                items.stream().map(OrderItemResponse::from).toList(),
                total(items));
    }

    /** Lignes du commerçant regroupées par commande (sans les paniers). */
    private Map<Order, List<OrderItem>> merchantItemsByOrder(String merchantId) {
        Map<Order, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
        for (OrderItem item : orderItemRepository.findByMerchantId(merchantId)) {
            if (item.getOrder().getStatus() != OrderStatus.PANIER) {
                itemsByOrder.computeIfAbsent(item.getOrder(), o -> new ArrayList<>()).add(item);
            }
        }
        return itemsByOrder;
    }

    private static BigDecimal total(List<OrderItem> items) {
        return items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** US3 : un commerçant voit les lignes de commande qui concernent ses articles. */
    @Transactional(readOnly = true)
    public List<MerchantOrderItemResponse> listMerchantOrderItems(CurrentUser user) {
        user.requireRole(Role.COMMERCANT);
        return orderItemRepository.findByMerchantId(user.id()).stream()
                // un panier n'est pas encore commandé : le commerçant ne le voit pas
                .filter(item -> item.getOrder().getStatus() != OrderStatus.PANIER)
                .map(MerchantOrderItemResponse::from)
                // les commandes les plus récentes d'abord
                .sorted(Comparator.comparing(MerchantOrderItemResponse::orderId).reversed())
                .toList();
    }
}
