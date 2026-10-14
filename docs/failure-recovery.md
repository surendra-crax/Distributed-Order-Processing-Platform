# Failure Recovery & Resilience Runbook

This document details failure modes, automatic recovery mechanisms, operational runbooks, and recovery procedures for the **Distributed Order Processing Platform**.

---

## 1. Failure Modes & Automatic Recovery Matrix

| Failure Scenario | Automatic System Behavior | Data Consistency Impact | Operational Action Required |
| :--- | :--- | :--- | :--- |
| **Kafka Broker Outage** | Outbox publisher retries with exponential backoff; outbox events remain safely persisted in PostgreSQL. | **Zero loss.** Orders succeed; event publishing catches up upon Kafka restoration. | None. Auto-recovers once Kafka returns online. |
| **Consumer Crash During Processing** | Consumer transaction rolls back (uncommitted Kafka offset & uncommitted `processed_events` row). | **Zero loss.** Next consumer rebalance re-delivers the message. | None. Exactly-once semantics guaranteed by deduplication table. |
| **Consumer Poison Pill (Malformed/Business Failure)** | `DefaultErrorHandler` retries 3 times with 1000ms backoff, then routes to `order.events.DLT`. | **Zero pipeline stall.** Healthy events continue processing. | Inspect and replay messages from DLT topic. |
| **Redis Cache Outage / Timeout** | `InventoryCacheService` catches connection/timeout exceptions, logs warnings, and falls back to PostgreSQL. | **Zero loss.** PostgreSQL remains authoritative source of truth. | Restore Redis cluster; cache repopulates automatically. |
| **Concurrent Checkout Contention** | Conditional atomic update fails row match (`available_quantity >= :qty`); throws `InsufficientInventoryException`. | **Zero overselling.** Unsuccessful transaction rolls back completely. | Customer is notified of stock shortage. |
| **Duplicate HTTP Order Request** | `IdempotencyService` detects matching SHA-256 hash in `idempotency_records` and returns cached HTTP 200 response. | **Zero duplicate orders.** Single inventory reservation maintained. | None. Client receives seamless idempotent response. |

---

## 2. Transactional Outbox Recovery Runbook

### Mechanism
The outbox poller runs every 500ms via `OutboxScheduler`:
```sql
SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50;
```
If publishing fails:
1. `retry_count` is incremented.
2. `error_message` records the root cause.
3. If `retry_count >= 5`, status transitions to `FAILED` to avoid blocking the queue.

### Manual Replay of Failed Outbox Events
To replay failed outbox events after fixing a downstream dependency:
```sql
-- Reset failed outbox records back to PENDING for automatic re-dispatch
UPDATE outbox_events
SET status = 'PENDING',
    retry_count = 0,
    error_message = NULL
WHERE status = 'FAILED';
```

---

## 3. Dead-Letter Topic (DLT) Inspection & Replay Runbook

### Dead-Letter Topic Configuration
- Primary Topic: `order.events`
- Dead-Letter Topic: `order.events.DLT`
- Producer Recoverer: `DeadLetterPublishingRecoverer`
- Headers injected by recoverer:
  - `kafka_original-topic`: `order.events`
  - `kafka_original-offset`: original message offset
  - `kafka_exception-message`: exception description
  - `kafka_exception-stacktrace`: complete stack trace

### Inspecting Messages on the Dead-Letter Topic
Using Kafka console consumer inside Docker Compose:
```bash
docker exec -it order_platform_kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.events.DLT \
  --from-beginning \
  --property print.headers=true \
  --property print.key=true
```

### Replaying Dead-Letter Messages
Once the root cause is resolved (e.g. data bug fixed or dependency restored):
```bash
# Pipe dead-letter messages back to the primary topic
docker exec -it order_platform_kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.events.DLT \
  --from-beginning | \
docker exec -i order_platform_kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.events
```
Because consumers enforce deduplication using `processed_events`, re-processing is safe: any event already applied is skipped without side effects.

---

## 4. Redis Resilience & Graceful Fallback (Fail-Open)

### Architectural Invariant
Redis is an **accelerator, not a source of truth**. 

### Fail-Open Verification Scenario
1. Stop Redis:
   ```bash
   docker stop order_platform_redis
   ```
2. Query stock availability:
   ```bash
   curl -i http://localhost:8080/api/v1/inventory/TECH-KEYB-01
   ```
3. **Observed Behavior**:
   - HTTP response is `200 OK` with valid data served directly from PostgreSQL.
   - Application log records:
     `WARN [corrId=...] Redis read failure for SKU 'TECH-KEYB-01': Connection refused. Falling back to PostgreSQL source of truth.`
   - Customer checkout and inventory check endpoints experience **zero downtime**.
4. Restart Redis:
   ```bash
   docker start order_platform_redis
   ```
5. Subsequent queries seamlessly re-populate the cache.

---

## 5. Idempotency Key Conflict Recovery

### Conflict Detection (HTTP 409)
If a client submits the same `Idempotency-Key` with a modified payload (e.g., changed quantity or different product), the platform rejects the request with HTTP 409 Conflict:
```json
{
  "type": "https://platform.com/errors/idempotency-key-conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Idempotency-Key '...' was already used with a different request payload."
}
```

### Resolution Runbook
1. If the client intended to place a **new order**, generate a fresh UUID for the `Idempotency-Key` header.
2. If the client intended to inspect the **original order**, query `GET /api/v1/orders/{orderId}` using the ID returned in the original response.
