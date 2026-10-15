package com.platform.orderprocessing.inventory.controller;

import com.platform.orderprocessing.inventory.dto.AddStockRequest;
import com.platform.orderprocessing.inventory.dto.CreateInventoryItemRequest;
import com.platform.orderprocessing.inventory.dto.InventoryResponse;
import com.platform.orderprocessing.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing product inventory levels and availability.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory Management", description = "Endpoints for checking product availability and replenishing stock")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/items")
    @Operation(summary = "Register a new product in inventory", description = "Adds a new SKU record to the authoritative inventory table")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "409", description = "Product SKU already exists")
    })
    public ResponseEntity<InventoryResponse> createItem(@Valid @RequestBody CreateInventoryItemRequest request) {
        InventoryResponse response = inventoryService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/stock")
    @Operation(summary = "Replenish stock for an existing SKU", description = "Increases available inventory count")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock replenished successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid quantity or SKU"),
            @ApiResponse(responseCode = "404", description = "SKU not found")
    })
    public ResponseEntity<InventoryResponse> addStock(@Valid @RequestBody AddStockRequest request) {
        InventoryResponse response = inventoryService.addStock(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get stock availability by SKU", description = "Returns available and reserved inventory counts for a product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory details retrieved"),
            @ApiResponse(responseCode = "404", description = "SKU not found")
    })
    public ResponseEntity<InventoryResponse> getInventoryBySku(@PathVariable String sku) {
        InventoryResponse response = inventoryService.getInventoryBySku(sku);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List all inventory items", description = "Returns all tracked inventory SKUs and quantities")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All inventory retrieved")
    })
    public ResponseEntity<List<InventoryResponse>> getAllInventory() {
        List<InventoryResponse> items = inventoryService.getAllInventory();
        return ResponseEntity.ok(items);
    }
}
