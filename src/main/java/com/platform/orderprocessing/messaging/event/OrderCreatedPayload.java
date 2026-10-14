package com.platform.orderprocessing.messaging.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload data for the OrderCreated event.
 */
public record OrderCreatedPayload(
        UUID orderId,
        String orderNumber,
        String customerId,
        String customerEmail,
        BigDecimal totalAmount,
        String currency,
        String status,
        List<OrderEventItem> items
) {
    public record OrderEventItem(
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
