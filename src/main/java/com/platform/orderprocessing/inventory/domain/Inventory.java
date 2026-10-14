package com.platform.orderprocessing.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing inventory stock for a product SKU.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true, length = 64)
    private String productId;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Inventory() {
    }

    public Inventory(UUID id, String productId, String sku, String productName,
                     int availableQuantity, int reservedQuantity, BigDecimal price) {
        this.id = id != null ? id : UUID.randomUUID();
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.price = price;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
        this.updatedAt = Instant.now();
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
        this.updatedAt = Instant.now();
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Inventory inventory)) {
            return false;
        }
        return Objects.equals(sku, inventory.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku);
    }
}
