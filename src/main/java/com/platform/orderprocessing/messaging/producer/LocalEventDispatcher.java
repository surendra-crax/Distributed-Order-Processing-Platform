package com.platform.orderprocessing.messaging.producer;

import com.platform.orderprocessing.messaging.consumer.OrderEventConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local asynchronous event dispatcher used in development/standalone mode
 * when external Kafka broker is not active. Simulates distributed event transmission,
 * network latency, and invokes the consumer deduplication logic.
 */
@Component
@Profile("local")
public class LocalEventDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(LocalEventDispatcher.class);

    private final OrderEventConsumer orderEventConsumer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LocalEventDispatcher(OrderEventConsumer orderEventConsumer) {
        this.orderEventConsumer = orderEventConsumer;
    }

    /**
     * Dispatches the event payload asynchronously with a small realistic delay (150ms).
     */
    public CompletableFuture<Void> dispatch(String partitionKey, String payload) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        LOG.debug("[LocalEventDispatcher] Asynchronously dispatching event for aggregate key '{}'", partitionKey);

        scheduler.schedule(() -> {
            try {
                orderEventConsumer.processEventPayload(payload);
                future.complete(null);
                LOG.info("[LocalEventDispatcher] Simulated Kafka event processed successfully for key: {}", partitionKey);
            } catch (Exception ex) {
                LOG.error("[LocalEventDispatcher] Simulated consumer failed for key {}: {}", partitionKey, ex.getMessage());
                future.completeExceptionally(ex);
            }
        }, 150, TimeUnit.MILLISECONDS);

        return future;
    }
}
