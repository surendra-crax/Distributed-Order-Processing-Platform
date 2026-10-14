package com.platform.orderprocessing.inventory.dto;

import com.platform.orderprocessing.inventory.domain.Inventory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public response representing inventory status for a SKU.
 */
public record InventoryResponse(
        UUID id,
        String productId,
        String sku,
        String productName,
        int availableQuantity,
        int reservedQuantity,
        BigDecimal price,
        Instant updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getSku(),
                inventory.getProductName(),
                inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(),
                inventory.getPrice(),
                inventory.getUpdatedAt()
        );
    }
}
