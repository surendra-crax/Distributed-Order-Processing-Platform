package com.platform.orderprocessing.common.error;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an order attempts to reserve stock exceeding available quantity.
 */
public class InsufficientInventoryException extends BusinessException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientInventoryException(String sku, int requested, int available) {
        super(String.format("Insufficient inventory for SKU '%s'. Requested: %d, Available: %d", sku, requested, available),
                HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-inventory");
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
