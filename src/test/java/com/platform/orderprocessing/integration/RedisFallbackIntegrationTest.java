package com.platform.orderprocessing.integration;

import com.platform.orderprocessing.inventory.dto.CreateInventoryItemRequest;
import com.platform.orderprocessing.inventory.dto.InventoryResponse;
import com.platform.orderprocessing.inventory.service.InventoryCacheService;
import com.platform.orderprocessing.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RedisFallbackIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @MockBean
    private org.springframework.data.redis.core.RedisOperations<String, InventoryResponse> redisOperations;

    private ValueOperations<String, InventoryResponse> valueOperations;

    private static final String FALLBACK_SKU = "TECH-REDIS-01";

    @BeforeEach
    void setUp() {
        valueOperations = mock(ValueOperations.class);
        when(redisOperations.opsForValue()).thenReturn(valueOperations);

        try {
            inventoryService.createItem(new CreateInventoryItemRequest(
                    "PROD-REDIS-01",
                    FALLBACK_SKU,
                    "Redis Fallback Item",
                    75,
                    new BigDecimal("120.00")
            ));
        } catch (Exception ignored) {
            // Already created
        }
    }

    @Test
    @DisplayName("Should serve inventory from Redis cache on cache HIT")
    void testCacheHitReturnsCachedData() {
        InventoryResponse cachedData = new InventoryResponse(
                java.util.UUID.randomUUID(),
                "PROD-REDIS-01",
                FALLBACK_SKU,
                "Cached Title",
                999, // Distinct cached quantity
                0,
                new BigDecimal("120.00"),
                java.time.Instant.now()
        );

        when(valueOperations.get("inventory:sku:" + FALLBACK_SKU)).thenReturn(cachedData);

        InventoryResponse response = inventoryService.getInventoryBySku(FALLBACK_SKU);

        assertThat(response).isNotNull();
        assertThat(response.availableQuantity()).isEqualTo(999);
        assertThat(response.productName()).isEqualTo("Cached Title");
    }

    @Test
    @DisplayName("Should gracefully fall back to PostgreSQL database when Redis throws connection error")
    void testRedisOutageFallsBackToDatabase() {
        // Simulate Redis being completely down / network partition
        when(valueOperations.get(anyString())).thenThrow(
                new RedisConnectionFailureException("Connection refused to Redis server at localhost:6379"));

        // Call should succeed without 500 error!
        InventoryResponse response = inventoryService.getInventoryBySku(FALLBACK_SKU);

        assertThat(response).isNotNull();
        assertThat(response.sku()).isEqualTo(FALLBACK_SKU);
        assertThat(response.availableQuantity()).isEqualTo(75);
    }

    @Test
    @DisplayName("Should safely handle Redis write failure without failing stock mutation")
    void testRedisWriteFailureHandledGracefully() {
        doThrow(new RedisConnectionFailureException("Redis timeout"))
                .when(valueOperations).set(anyString(), any(), any());

        // Call should succeed and populate from DB despite Redis write exception
        InventoryResponse response = inventoryService.getInventoryBySku(FALLBACK_SKU);

        assertThat(response).isNotNull();
        assertThat(response.sku()).isEqualTo(FALLBACK_SKU);
    }
}
