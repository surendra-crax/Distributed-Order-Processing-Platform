package com.platform.orderprocessing.order.domain;

import com.platform.orderprocessing.common.error.InvalidOrderStateException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity representing a Customer Order.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Order() {
    }

    public Order(UUID id, String orderNumber, String customerId, String customerEmail,
                 BigDecimal totalAmount, String currency) {
        this.id = id != null ? id : UUID.randomUUID();
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.currency = currency != null ? currency : "USD";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /**
     * Enforces valid order lifecycle state transitions.
     *
     * @param newStatus desired target status
     */
    public void transitionTo(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidOrderStateException(String.format(
                    "Invalid state transition for order %s: cannot transition from %s to %s. Allowed transitions: %s",
                    orderNumber, status, newStatus, status.allowableTransitions()
            ));
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        this.updatedAt = Instant.now();
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return items;
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
        if (!(o instanceof Order order)) {
            return false;
        }
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
