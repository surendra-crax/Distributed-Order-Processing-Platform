package com.platform.orderprocessing.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Item specification when creating an order.
 */
public record OrderItemRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {}
