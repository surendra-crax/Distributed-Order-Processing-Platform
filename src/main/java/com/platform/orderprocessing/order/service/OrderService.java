package com.platform.orderprocessing.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderprocessing.common.error.BusinessException;
import com.platform.orderprocessing.common.error.ResourceNotFoundException;
import com.platform.orderprocessing.inventory.domain.Inventory;
import com.platform.orderprocessing.inventory.repository.InventoryRepository;
import com.platform.orderprocessing.inventory.service.InventoryService;
import com.platform.orderprocessing.order.domain.Order;
import com.platform.orderprocessing.order.domain.OrderItem;
import com.platform.orderprocessing.order.domain.OrderStatus;
import com.platform.orderprocessing.order.dto.CreateOrderRequest;
import com.platform.orderprocessing.order.dto.OrderEventResponse;
import com.platform.orderprocessing.order.dto.OrderItemRequest;
import com.platform.orderprocessing.order.dto.OrderResponse;
import com.platform.orderprocessing.order.repository.OrderRepository;
import com.platform.orderprocessing.outbox.domain.OutboxEvent;
import com.platform.orderprocessing.outbox.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating order creation, precision financial calculations,
 * atomic inventory reservations, and transactional outbox persistence.
 */
@Service
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        InventoryRepository inventoryRepository,
                        InventoryService inventoryService,
                        OutboxEventRepository outboxEventRepository,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an order with strict server-side price computation and atomic inventory reservation.
     * Persists order, order_items, and outbox_events in a single local database transaction.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        LOG.info("Initiating order creation for customer: {}, items count: {}", request.customerId(), request.items().size());

        // 1. Consolidate requested quantities by SKU
        Map<String, Integer> requestedQuantities = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            if (item.quantity() <= 0) {
                throw new BusinessException("Item quantity must be greater than zero for SKU: " + item.sku(),
                        HttpStatus.BAD_REQUEST, "invalid-quantity");
            }
            requestedQuantities.merge(item.sku(), item.quantity(), Integer::sum);
        }

        // 2. Reserve inventory atomically (throws InsufficientInventoryException if insufficient)
        inventoryService.reserveStock(requestedQuantities);

        // 3. Build Order and calculate financial amounts strictly on server using BigDecimal
        UUID orderId = UUID.randomUUID();
        String orderNumber = generateOrderNumber();
        String currency = request.currency() != null ? request.currency().toUpperCase() : "USD";

        Order order = new Order(orderId, orderNumber, request.customerId(), request.customerEmail(), BigDecimal.ZERO, currency);
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            String sku = entry.getKey();
            int quantity = entry.getValue();

            Inventory inventory = inventoryRepository.findBySku(sku)
                    .orElseThrow(() -> new ResourceNotFoundException("SKU not found in catalog: " + sku));

            BigDecimal unitPrice = inventory.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem(
                    UUID.randomUUID(),
                    order,
                    inventory.getProductId(),
                    sku,
                    inventory.getProductName(),
                    quantity,
                    unitPrice,
                    subtotal
            );
            order.addItem(orderItem);
        }

        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        Order savedOrder = orderRepository.save(order);
        LOG.info("Order saved successfully. ID: {}, orderNumber: {}, total: {} {}",
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotalAmount(), savedOrder.getCurrency());

        // 4. Record transactional outbox event in the EXACT SAME local transaction
        recordOrderOutboxEvent(savedOrder, "OrderCreated");

        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Pageable pageable, OrderStatus status) {
        Page<Order> orders = status != null
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);
        return orders.map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> getOrderEvents(UUID orderId) {
        // Verify order exists
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order not found with ID: " + orderId);
        }
        return outboxEventRepository.findByAggregateId(orderId.toString()).stream()
                .map(OrderEventResponse::from)
                .toList();
    }

    @Transactional
    public void updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        order.transitionTo(newStatus);
        orderRepository.save(order);
        LOG.info("Updated order {} status from {} to {}", order.getOrderNumber(), currentStatus, newStatus);

        recordOrderOutboxEvent(order, "Order" + capitalize(newStatus.name().toLowerCase()));
    }

    private void recordOrderOutboxEvent(Order order, String eventType) {
        try {
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("eventId", UUID.randomUUID().toString());
            eventPayload.put("eventType", eventType);
            eventPayload.put("aggregateType", "ORDER");
            eventPayload.put("aggregateId", order.getId().toString());
            eventPayload.put("orderId", order.getId().toString());
            eventPayload.put("orderNumber", order.getOrderNumber());
            eventPayload.put("customerId", order.getCustomerId());
            eventPayload.put("totalAmount", order.getTotalAmount());
            eventPayload.put("status", order.getStatus().name());
            eventPayload.put("timestamp", Instant.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(eventPayload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID(),
                    "ORDER",
                    order.getId().toString(),
                    eventType,
                    jsonPayload
            );

            outboxEventRepository.save(outboxEvent);
            LOG.debug("Persisted outbox event {} for order {}", eventType, order.getId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }

    private String generateOrderNumber() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "ORD-" + timestamp + "-" + randomSuffix;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
