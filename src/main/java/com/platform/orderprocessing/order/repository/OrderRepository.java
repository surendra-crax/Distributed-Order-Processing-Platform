package com.platform.orderprocessing.order.repository;

import com.platform.orderprocessing.order.domain.Order;
import com.platform.orderprocessing.order.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for customer Orders.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);
}
