-- =============================================================================
-- Migration V1: Initial Schema for Distributed Order Processing Platform
-- Authoritative persistent storage for orders, inventory, outbox, and idempotency
-- =============================================================================

-- 1. Orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id VARCHAR(64) NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);

-- 2. Order items table
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(64) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    subtotal NUMERIC(12, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_sku ON order_items(sku);

-- 3. Inventory table with concurrency protection
CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL UNIQUE,
    sku VARCHAR(64) NOT NULL UNIQUE,
    product_name VARCHAR(255) NOT NULL,
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_sku ON inventory(sku);

-- 4. Transactional Outbox table
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);

-- 5. Consumer deduplication table (at-least-once -> exactly-once business effects)
CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    consumer_group VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer_group)
);

-- 6. API Idempotency tracking table
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_path VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_code INT,
    response_body TEXT,
    order_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_records(expires_at);
