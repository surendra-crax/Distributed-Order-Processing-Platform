package com.platform.orderprocessing.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderprocessing.idempotency.domain.IdempotencyRecord;
import com.platform.orderprocessing.idempotency.service.IdempotencyService;
import com.platform.orderprocessing.order.domain.OrderStatus;
import com.platform.orderprocessing.order.dto.CreateOrderRequest;
import com.platform.orderprocessing.order.dto.OrderEventResponse;
import com.platform.orderprocessing.order.dto.OrderResponse;
import com.platform.orderprocessing.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for order creation, order query, and lifecycle audit history.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Management", description = "Endpoints for creating and tracking customer orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderController(OrderService orderService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Create a new order",
            description = "Creates an order, reserves inventory atomically, and emits an outbox event. "
                    + "Supports the Idempotency-Key header to prevent duplicate orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay: cached order returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or quantities"),
            @ApiResponse(responseCode = "409", description = "Idempotency key collision or concurrent execution"),
            @ApiResponse(responseCode = "422", description = "Insufficient inventory available")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "Unique client-generated idempotency key (e.g. UUID)")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            requestJson = request.toString();
        }

        // 1. Check idempotency if header supplied
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, "/api/v1/orders", requestJson);
            if (cached.isPresent()) {
                IdempotencyRecord record = cached.get();
                try {
                    OrderResponse cachedResponse = objectMapper.readValue(record.getResponseBody(), OrderResponse.class);
                    return ResponseEntity.status(HttpStatus.OK).body(cachedResponse);
                } catch (Exception e) {
                    // Fallback to fresh retrieval if deserialization fails
                    if (record.getOrderId() != null) {
                        return ResponseEntity.ok(orderService.getOrderById(record.getOrderId()));
                    }
                }
            }
        }

        // 2. Execute business transaction
        try {
            OrderResponse response = orderService.createOrder(request);

            // 3. Mark idempotency complete
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                try {
                    String responseJson = objectMapper.writeValueAsString(response);
                    idempotencyService.markCompleted(idempotencyKey, HttpStatus.CREATED.value(), responseJson, response.id());
                } catch (Exception ignored) {
                    // Logging handled inside idempotency service
                }
            }

            URI location = URI.create("/api/v1/orders/" + response.id());
            return ResponseEntity.created(location).body(response);
        } catch (RuntimeException ex) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyService.release(idempotencyKey);
            }
            throw ex;
        }
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID", description = "Retrieves order status, line items, and totals")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order retrieved"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID orderId) {
        OrderResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "List orders with pagination", description = "Returns a paginated list of orders with optional status filter")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders list retrieved")
    })
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderResponse> orders = orderService.getOrders(pageRequest, status);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}/events")
    @Operation(summary = "Get order audit trail and lifecycle events",
            description = "Returns immutable event history recorded for the order aggregate")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events list retrieved"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<List<OrderEventResponse>> getOrderEvents(@PathVariable UUID orderId) {
        List<OrderEventResponse> events = orderService.getOrderEvents(orderId);
        return ResponseEntity.ok(events);
    }
}
