package com.shoploc.shoplocorderservice.repository;

import com.shoploc.shoplocorderservice.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByMerchantIdOrderByIdDesc(String merchantId);
}
