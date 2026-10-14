-- =============================================================================
-- Migration V2: Sample Seed Data for Testing and Local Development
-- =============================================================================

INSERT INTO inventory (id, product_id, sku, product_name, available_quantity, reserved_quantity, price, version, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'PROD-KEYB-01', 'TECH-KEYB-01', 'Mechanical Gaming Keyboard', 100, 0, 79.99, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000002', 'PROD-MOUS-02', 'TECH-MOUS-02', 'Ergonomic Wireless Mouse', 150, 0, 49.99, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000003', 'PROD-MONI-03', 'TECH-MONI-03', '27-inch 4K UHD Monitor', 25, 0, 349.99, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000004', 'PROD-HUB-04', 'TECH-HUB-04', 'USB-C 7-in-1 Multiport Adapter', 200, 0, 29.99, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000005', 'PROD-HEAD-05', 'TECH-HEAD-05', 'ANC Wireless Headphones', 50, 0, 199.99, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
