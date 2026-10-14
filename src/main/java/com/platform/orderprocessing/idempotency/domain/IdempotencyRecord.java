package com.platform.orderprocessing.idempotency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity tracking API Idempotency Keys across application restarts.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_path", nullable = false)
    private String requestPath;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestPath, String requestHash, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestHash = requestHash;
        this.status = STATUS_IN_PROGRESS;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void markCompleted(int responseCode, String responseBody, UUID orderId) {
        this.status = STATUS_COMPLETED;
        this.responseCode = responseCode;
        this.responseBody = responseBody;
        this.orderId = orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getStatus() {
        return status;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdempotencyRecord that)) {
            return false;
        }
        return Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey);
    }
}
