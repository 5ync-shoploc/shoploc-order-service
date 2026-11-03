package com.shoploc.shoplocorderservice.repository;

import com.shoploc.shoplocorderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
