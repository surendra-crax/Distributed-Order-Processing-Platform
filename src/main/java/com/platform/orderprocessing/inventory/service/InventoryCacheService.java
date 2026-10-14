package com.platform.orderprocessing.inventory.service;

import com.platform.orderprocessing.inventory.dto.InventoryResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Service;

/**
 * Resilient cache-aside service for inventory availability.
 * Follows a fail-open pattern: any Redis connection or timeout error is caught,
 * logged, and transparently bypassed so PostgreSQL continues serving traffic.
 */
@Service
public class InventoryCacheService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryCacheService.class);
    private static final String KEY_PREFIX = "inventory:sku:";

    private final RedisOperations<String, InventoryResponse> redisOperations;
    private final Duration ttl;

    public InventoryCacheService(RedisOperations<String, InventoryResponse> redisOperations,
                                 @Value("${platform.cache.inventory-ttl-seconds:300}") long ttlSeconds) {
        this.redisOperations = redisOperations;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Attempts to read inventory from Redis.
     * Returns empty on cache miss or Redis error.
     */
    public Optional<InventoryResponse> getCachedInventory(String sku) {
        try {
            String key = KEY_PREFIX + sku;
            InventoryResponse cached = redisOperations.opsForValue().get(key);
            if (cached != null) {
                LOG.debug("Cache HIT for SKU: {}", sku);
                return Optional.of(cached);
            }
            LOG.debug("Cache MISS for SKU: {}", sku);
        } catch (Exception ex) {
            LOG.warn("Redis read failure for SKU '{}': {}. Falling back to PostgreSQL source of truth.", sku, ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Writes inventory data to Redis with configured TTL.
     * Logs warning and continues if Redis is unavailable.
     */
    public void cacheInventory(String sku, InventoryResponse response) {
        try {
            String key = KEY_PREFIX + sku;
            redisOperations.opsForValue().set(key, response, ttl);
            LOG.debug("Cached SKU: {} with TTL {}s", sku, ttl.toSeconds());
        } catch (Exception ex) {
            LOG.warn("Redis write failure for SKU '{}': {}. Proceeding without cache.", sku, ex.getMessage());
        }
    }

    /**
     * Evicts inventory from Redis upon stock mutation.
     */
    public void evictInventory(String sku) {
        try {
            String key = KEY_PREFIX + sku;
            redisOperations.delete(key);
            LOG.debug("Evicted cache for SKU: {}", sku);
        } catch (Exception ex) {
            LOG.warn("Redis eviction failure for SKU '{}': {}.", sku, ex.getMessage());
        }
    }
}
