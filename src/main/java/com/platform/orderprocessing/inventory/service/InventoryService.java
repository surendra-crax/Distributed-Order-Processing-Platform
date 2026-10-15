package com.platform.orderprocessing.inventory.service;

import com.platform.orderprocessing.common.error.BusinessException;
import com.platform.orderprocessing.common.error.InsufficientInventoryException;
import com.platform.orderprocessing.common.error.ResourceNotFoundException;
import com.platform.orderprocessing.inventory.domain.Inventory;
import com.platform.orderprocessing.inventory.dto.AddStockRequest;
import com.platform.orderprocessing.inventory.dto.CreateInventoryItemRequest;
import com.platform.orderprocessing.inventory.dto.InventoryResponse;
import com.platform.orderprocessing.inventory.repository.InventoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing product stock and thread-safe inventory reservations.
 */
@Service
public class InventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryCacheService inventoryCacheService;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryCacheService inventoryCacheService) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryCacheService = inventoryCacheService;
    }

    /**
     * Registers a new product SKU in inventory.
     */
    @Transactional
    public InventoryResponse createItem(CreateInventoryItemRequest request) {
        if (inventoryRepository.existsBySku(request.sku())) {
            throw new BusinessException("Product with SKU '" + request.sku() + "' already exists.",
                    HttpStatus.CONFLICT, "duplicate-sku");
        }

        Inventory inventory = new Inventory(
                UUID.randomUUID(),
                request.productId(),
                request.sku(),
                request.productName(),
                request.availableQuantity(),
                0,
                request.price()
        );

        Inventory saved = inventoryRepository.save(inventory);
        LOG.info("Created inventory item SKU: {}, available: {}", saved.getSku(), saved.getAvailableQuantity());
        InventoryResponse response = InventoryResponse.from(saved);
        inventoryCacheService.cacheInventory(saved.getSku(), response);
        return response;
    }

    /**
     * Replenishes stock for a SKU.
     */
    @Transactional
    public InventoryResponse addStock(AddStockRequest request) {
        Inventory inventory = inventoryRepository.findBySku(request.sku())
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found: " + request.sku()));

        inventoryRepository.addStockQuantity(request.sku(), request.quantity());
        Inventory refreshed = inventoryRepository.findBySku(request.sku())
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found: " + request.sku()));

        // Invalidate cache upon stock replenishment
        inventoryCacheService.evictInventory(request.sku());

        LOG.info("Added {} units to SKU: {}. New available: {}", request.quantity(), request.sku(), refreshed.getAvailableQuantity());
        return InventoryResponse.from(refreshed);
    }

    /**
     * Retrieves current inventory levels for a SKU.
     * Uses Redis read-through cache with seamless fallback to PostgreSQL.
     */
    @Transactional(readOnly = true)
    public InventoryResponse getInventoryBySku(String sku) {
        // 1. Try cache read
        var cached = inventoryCacheService.getCachedInventory(sku);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Cache miss: read authoritative PostgreSQL database
        Inventory inventory = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found: " + sku));
        InventoryResponse response = InventoryResponse.from(inventory);

        // 3. Populate cache
        inventoryCacheService.cacheInventory(sku, response);
        return response;
    }

    /**
     * Reserves stock for multiple items in a deterministic order.
     * Prevents database deadlocks and overselling under high concurrency.
     *
     * @param items map of SKU to quantity requested
     */
    @Transactional
    public void reserveStock(Map<String, Integer> items) {
        // Sort keys to prevent deadlock when multiple threads reserve intersecting sets of SKUs
        List<String> sortedSkus = items.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        for (String sku : sortedSkus) {
            int requestedQty = items.get(sku);
            int rowsUpdated = inventoryRepository.decrementAvailableStock(sku, requestedQty);

            if (rowsUpdated == 0) {
                // Determine current stock for clear error messaging
                Inventory current = inventoryRepository.findBySku(sku)
                        .orElseThrow(() -> new ResourceNotFoundException("SKU not found: " + sku));
                LOG.warn("Reservation rejected: SKU {} requires {}, but only {} available",
                        sku, requestedQty, current.getAvailableQuantity());
                throw new InsufficientInventoryException(sku, requestedQty, current.getAvailableQuantity());
            }

            // Evict modified SKU from cache
            inventoryCacheService.evictInventory(sku);
            LOG.debug("Reserved {} units of SKU: {}", requestedQty, sku);
        }
    }

    /**
     * Compensating action: releases reserved stock back to available pool.
     */
    @Transactional
    public void releaseStock(Map<String, Integer> items) {
        items.forEach((sku, qty) -> {
            inventoryRepository.releaseReservedStock(sku, qty);
            inventoryCacheService.evictInventory(sku);
            LOG.info("Released {} reserved units for SKU: {}", qty, sku);
        });
    }

    /**
     * Retrieves all tracked inventory items sorted by SKU.
     */
    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Inventory::getSku))
                .map(InventoryResponse::from)
                .toList();
    }
}
