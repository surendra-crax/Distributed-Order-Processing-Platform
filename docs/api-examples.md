# API Examples & curl Reference

This guide provides realistic, runnable `curl` commands demonstrating every endpoint and edge-case behavior of the **Distributed Order Processing Platform**.

Base URL: `http://localhost:8080`

---

## 1. System Health & Readiness

### Health Check (Liveness & Readiness Probes)
```bash
curl -X GET "http://localhost:8080/actuator/health" \
  -H "Accept: application/json"
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.2.5"
      }
    }
  },
  "groups": [
    "liveness",
    "readiness"
  ]
}
```

---

## 2. Inventory Management

### Register a New Product SKU
```bash
curl -X POST "http://localhost:8080/api/v1/inventory/items" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PROD-MIC-01",
    "sku": "TECH-MIC-01",
    "productName": "Studio USB Condenser Microphone",
    "availableQuantity": 50,
    "price": 89.99
  }'
```

**Response (201 Created):**
```json
{
  "id": "e3b0c442-98fc-1c14-9afb-4c7fa44b80e9",
  "productId": "PROD-MIC-01",
  "sku": "TECH-MIC-01",
  "productName": "Studio USB Condenser Microphone",
  "availableQuantity": 50,
  "reservedQuantity": 0,
  "price": 89.99,
  "updatedAt": "2026-10-14T12:00:00Z"
}
```

### Check Stock Availability (Cached via Redis)
```bash
curl -X GET "http://localhost:8080/api/v1/inventory/TECH-KEYB-01" \
  -H "Accept: application/json"
```

**Response (200 OK):**
```json
{
  "id": "a0000000-0000-0000-0000-000000000001",
  "productId": "PROD-KEYB-01",
  "sku": "TECH-KEYB-01",
  "productName": "Mechanical Gaming Keyboard",
  "availableQuantity": 100,
  "reservedQuantity": 0,
  "price": 79.99,
  "updatedAt": "2026-10-14T12:00:00Z"
}
```

### Replenish Stock for an Existing SKU
```bash
curl -X POST "http://localhost:8080/api/v1/inventory/stock" \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "TECH-KEYB-01",
    "quantity": 25
  }'
```

**Response (200 OK):**
```json
{
  "id": "a0000000-0000-0000-0000-000000000001",
  "productId": "PROD-KEYB-01",
  "sku": "TECH-KEYB-01",
  "productName": "Mechanical Gaming Keyboard",
  "availableQuantity": 125,
  "reservedQuantity": 0,
  "price": 79.99,
  "updatedAt": "2026-10-14T12:01:00Z"
}
```

---

## 3. Order Placement & Idempotency

### Create an Order with an Idempotency-Key
```bash
curl -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: b3f28d88-75b2-4d51-9f2d-88e2c0e81b67" \
  -d '{
    "customerId": "CUST-402",
    "customerEmail": "alex.dev@example.com",
    "items": [
      {
        "sku": "TECH-KEYB-01",
        "quantity": 2
      },
      {
        "sku": "TECH-MOUS-02",
        "quantity": 1
      }
    ],
    "currency": "USD"
  }'
```

**Response (201 Created):**
```json
{
  "id": "8f8b340a-5b12-4fce-bc2f-38beea39268f",
  "orderNumber": "ORD-20261014120000-9B2F",
  "customerId": "CUST-402",
  "customerEmail": "alex.dev@example.com",
  "status": "PENDING",
  "totalAmount": 209.97,
  "currency": "USD",
  "items": [
    {
      "id": "1c7a8b41-2091-4e4b-97e3-0599a16fbd32",
      "productId": "PROD-KEYB-01",
      "sku": "TECH-KEYB-01",
      "productName": "Mechanical Gaming Keyboard",
      "quantity": 2,
      "unitPrice": 79.99,
      "subtotal": 159.98
    },
    {
      "id": "2d8b9c52-3102-5f5c-08f4-1600b27fce43",
      "productId": "PROD-MOUS-02",
      "sku": "TECH-MOUS-02",
      "productName": "Ergonomic Wireless Mouse",
      "quantity": 1,
      "unitPrice": 49.99,
      "subtotal": 49.99
    }
  ],
  "createdAt": "2026-10-14T12:00:00Z",
  "updatedAt": "2026-10-14T12:00:00Z"
}
```

### Idempotent Replay (Repeat Identical Request with Same Key)
```bash
curl -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: b3f28d88-75b2-4d51-9f2d-88e2c0e81b67" \
  -d '{
    "customerId": "CUST-402",
    "customerEmail": "alex.dev@example.com",
    "items": [
      {
        "sku": "TECH-KEYB-01",
        "quantity": 2
      },
      {
        "sku": "TECH-MOUS-02",
        "quantity": 1
      }
    ],
    "currency": "USD"
  }'
```

**Response (200 OK — Cached Replay, No Duplicate Order Created):**
```json
{
  "id": "8f8b340a-5b12-4fce-bc2f-38beea39268f",
  "orderNumber": "ORD-20261014120000-9B2F",
  "customerId": "CUST-402",
  "customerEmail": "alex.dev@example.com",
  "status": "PENDING",
  "totalAmount": 209.97,
  "currency": "USD",
  "items": [...]
}
```

### Idempotency Key Conflict (Reusing Key with Different Payload)
```bash
curl -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: b3f28d88-75b2-4d51-9f2d-88e2c0e81b67" \
  -d '{
    "customerId": "CUST-402",
    "customerEmail": "alex.dev@example.com",
    "items": [
      {
        "sku": "TECH-KEYB-01",
        "quantity": 10
      }
    ],
    "currency": "USD"
  }'
```

**Response (409 Conflict):**
```json
{
  "type": "https://platform.com/errors/idempotency-key-conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Idempotency-Key 'b3f28d88-75b2-4d51-9f2d-88e2c0e81b67' was already used with a different request payload.",
  "instance": "/api/v1/orders",
  "timestamp": "2026-10-14T12:00:05Z",
  "correlationId": "50bb2781-79e5-4720-a6ce-5895fa53f478"
}
```

---

## 4. Querying Orders & Lifecycle Audit Trail

### Retrieve Order by ID
```bash
curl -X GET "http://localhost:8080/api/v1/orders/8f8b340a-5b12-4fce-bc2f-38beea39268f" \
  -H "Accept: application/json"
```

**Response (200 OK — Note status has transitioned to CONFIRMED by Kafka consumer):**
```json
{
  "id": "8f8b340a-5b12-4fce-bc2f-38beea39268f",
  "orderNumber": "ORD-20261014120000-9B2F",
  "customerId": "CUST-402",
  "customerEmail": "alex.dev@example.com",
  "status": "CONFIRMED",
  "totalAmount": 209.97,
  "currency": "USD",
  "items": [...]
}
```

### List Orders with Pagination and Status Filter
```bash
curl -X GET "http://localhost:8080/api/v1/orders?page=0&size=10&status=CONFIRMED" \
  -H "Accept: application/json"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "8f8b340a-5b12-4fce-bc2f-38beea39268f",
      "orderNumber": "ORD-20261014120000-9B2F",
      "status": "CONFIRMED",
      "totalAmount": 209.97
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### View Order Event History (Audit Trail)
```bash
curl -X GET "http://localhost:8080/api/v1/orders/8f8b340a-5b12-4fce-bc2f-38beea39268f/events" \
  -H "Accept: application/json"
```

**Response (200 OK):**
```json
[
  {
    "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "aggregateId": "8f8b340a-5b12-4fce-bc2f-38beea39268f",
    "eventType": "OrderCreated",
    "status": "PUBLISHED",
    "retryCount": 0,
    "createdAt": "2026-10-14T12:00:00Z",
    "publishedAt": "2026-10-14T12:00:00.520Z",
    "payload": "{\"eventId\":\"f47ac10b-58cc-4372-a567-0e02b2c3d479\",\"eventType\":\"OrderCreated\",\"orderId\":\"8f8b340a-5b12-4fce-bc2f-38beea39268f\",\"status\":\"PENDING\"}"
  },
  {
    "eventId": "e15db21c-69dd-5483-b678-1f13c3d4e580",
    "aggregateId": "8f8b340a-5b12-4fce-bc2f-38beea39268f",
    "eventType": "OrderConfirmed",
    "status": "PUBLISHED",
    "retryCount": 0,
    "createdAt": "2026-10-14T12:00:01Z",
    "publishedAt": "2026-10-14T12:00:01.310Z",
    "payload": "{\"eventId\":\"e15db21c-69dd-5483-b678-1f13c3d4e580\",\"eventType\":\"OrderConfirmed\",\"orderId\":\"8f8b340a-5b12-4fce-bc2f-38beea39268f\",\"status\":\"CONFIRMED\"}"
  }
]
```

---

## 5. Error Scenarios & RFC 7807 Problem Details

### Insufficient Inventory (422 Unprocessable Entity)
```bash
curl -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-999",
    "customerEmail": "buyer@example.com",
    "items": [
      {
        "sku": "TECH-KEYB-01",
        "quantity": 50000
      }
    ]
  }'
```

**Response (422 Unprocessable Entity):**
```json
{
  "type": "https://platform.com/errors/insufficient-inventory",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Insufficient inventory for SKU 'TECH-KEYB-01'. Requested: 50000, Available: 125",
  "instance": "/api/v1/orders",
  "timestamp": "2026-10-14T12:02:10Z",
  "correlationId": "86bc5418-d7e2-411a-8ce4-436f02377a29"
}
```

### Invalid Input / Validation Failure (400 Bad Request)
```bash
curl -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "",
    "customerEmail": "not-an-email",
    "items": []
  }'
```

**Response (400 Bad Request):**
```json
{
  "type": "https://platform.com/errors/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more request parameters failed validation.",
  "instance": "/api/v1/orders",
  "timestamp": "2026-10-14T12:03:00Z",
  "correlationId": "091ce874-8893-4a18-9102-127e21a221f0",
  "validationErrors": {
    "customerId": "Customer ID is required",
    "customerEmail": "Customer email must be a valid email address",
    "items": "Order must contain at least one item"
  }
}
```
