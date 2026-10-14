package com.platform.orderprocessing.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to replenish stock for an existing SKU.
 */
public record AddStockRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @Min(value = 1, message = "Quantity to add must be at least 1")
        int quantity
) {}
