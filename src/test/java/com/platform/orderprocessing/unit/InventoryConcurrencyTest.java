package com.platform.orderprocessing.unit;

import com.platform.orderprocessing.common.error.InsufficientInventoryException;
import com.platform.orderprocessing.inventory.domain.Inventory;
import com.platform.orderprocessing.inventory.dto.CreateInventoryItemRequest;
import com.platform.orderprocessing.inventory.dto.InventoryResponse;
import com.platform.orderprocessing.inventory.repository.InventoryRepository;
import com.platform.orderprocessing.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private static final String CONCURRENT_SKU = "TECH-RACE-01";

    @BeforeEach
    void setUp() {
        inventoryRepository.findBySku(CONCURRENT_SKU).ifPresent(inventoryRepository::delete);

        CreateInventoryItemRequest request = new CreateInventoryItemRequest(
                "PROD-RACE-01",
                CONCURRENT_SKU,
                "Limited Edition Wireless Keyboard",
                10, // Exactly 10 units available
                new BigDecimal("99.99")
        );
        inventoryService.createItem(request);
    }

    @Test
    @DisplayName("Should prevent overselling when 50 concurrent threads race to reserve stock of 10 items")
    void testConcurrentStockReservationPreventsOverselling() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger failedReservations = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Ensure all threads race at the exact same millisecond
                    inventoryService.reserveStock(Map.of(CONCURRENT_SKU, 1));
                    successfulReservations.incrementAndGet();
                } catch (InsufficientInventoryException e) {
                    failedReservations.incrementAndGet();
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release the latch to start the race condition simulation
        startLatch.countDown();
        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finished).isTrue();
        assertThat(successfulReservations.get()).isEqualTo(10);
        assertThat(failedReservations.get()).isEqualTo(40);

        // Verify database state: available must be 0, reserved must be 10, never negative!
        Inventory finalInventory = inventoryRepository.findBySku(CONCURRENT_SKU).orElseThrow();
        assertThat(finalInventory.getAvailableQuantity()).isEqualTo(0);
        assertThat(finalInventory.getReservedQuantity()).isEqualTo(10);
    }
}
