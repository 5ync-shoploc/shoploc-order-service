package com.shoploc.shoplocorderservice.repository;

import com.shoploc.shoplocorderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClientIdOrderByIdDesc(String clientId);

    Optional<Order> findByIdAndClientId(Long id, String clientId);
}
