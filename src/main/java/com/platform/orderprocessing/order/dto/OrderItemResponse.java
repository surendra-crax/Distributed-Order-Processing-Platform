package com.platform.orderprocessing.order.dto;

import com.platform.orderprocessing.order.domain.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representation of an individual order line item in response payloads.
 */
public record OrderItemResponse(
        UUID id,
        String productId,
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
