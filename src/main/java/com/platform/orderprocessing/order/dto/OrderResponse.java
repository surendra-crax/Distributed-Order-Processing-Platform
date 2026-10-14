package com.platform.orderprocessing.order.dto;

import com.platform.orderprocessing.order.domain.Order;
import com.platform.orderprocessing.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public response representing a customer Order and its line items.
 */
public record OrderResponse(
        UUID id,
        String orderNumber,
        String customerId,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(OrderItemResponse::from).toList()
                : List.of();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
