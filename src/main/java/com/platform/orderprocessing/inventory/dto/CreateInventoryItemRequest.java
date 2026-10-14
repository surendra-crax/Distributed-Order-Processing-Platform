package com.platform.orderprocessing.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request payload to register a new product SKU in inventory.
 */
public record CreateInventoryItemRequest(
        @NotBlank(message = "Product ID is required")
        String productId,

        @NotBlank(message = "SKU is required")
        String sku,

        @NotBlank(message = "Product name is required")
        String productName,

        @Min(value = 0, message = "Available quantity cannot be negative")
        int availableQuantity,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price
) {}
