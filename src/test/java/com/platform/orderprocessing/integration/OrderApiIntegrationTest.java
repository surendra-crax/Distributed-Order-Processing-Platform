package com.platform.orderprocessing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderprocessing.inventory.dto.CreateInventoryItemRequest;
import com.platform.orderprocessing.inventory.service.InventoryService;
import com.platform.orderprocessing.order.dto.CreateOrderRequest;
import com.platform.orderprocessing.order.dto.OrderItemRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryService inventoryService;

    private static final String TEST_SKU = "TECH-INTEG-01";

    @BeforeEach
    void setUp() {
        try {
            inventoryService.createItem(new CreateInventoryItemRequest(
                    "PROD-INTEG-01",
                    TEST_SKU,
                    "Integration Test SKU",
                    20,
                    new BigDecimal("50.00")
            ));
        } catch (Exception ignored) {
            // Already created in previous run
        }
    }

    @Test
    @DisplayName("Should create order, verify idempotency replay, and prevent conflicting key reuse")
    void testOrderCreationAndIdempotencyFlow() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        CreateOrderRequest request = new CreateOrderRequest(
                "CUST-API-1",
                "buyer@test.com",
                List.of(new OrderItemRequest(TEST_SKU, 2)),
                "USD"
        );

        // 1. Initial order creation -> 201 Created
        String responseContent = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount", is(100.0)))
                .andExpect(header().exists("X-Correlation-ID"))
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(responseContent).get("id").asText();

        // 2. Exact same request with same Idempotency-Key -> 200 OK (cached replay)
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(orderId)))
                .andExpect(jsonPath("$.totalAmount", is(100.0)));

        // 3. Same Idempotency-Key with DIFFERENT payload -> 409 Conflict
        CreateOrderRequest conflictingRequest = new CreateOrderRequest(
                "CUST-API-1",
                "buyer@test.com",
                List.of(new OrderItemRequest(TEST_SKU, 5)), // Different quantity!
                "USD"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("https://platform.com/errors/idempotency-key-conflict")));

        // 4. Retrieve order by ID -> 200 OK
        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(orderId)))
                .andExpect(jsonPath("$.customerId", is("CUST-API-1")));

        // 5. Query order event history -> 200 OK
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType", is("OrderCreated")));
    }

    @Test
    @DisplayName("Should reject order with 422 Unprocessable Entity when requested quantity exceeds inventory")
    void testInsufficientInventoryRejection() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "CUST-API-2",
                "buyer2@test.com",
                List.of(new OrderItemRequest(TEST_SKU, 99999)), // Insufficient
                "USD"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type", is("https://platform.com/errors/insufficient-inventory")))
                .andExpect(jsonPath("$.title", is("Unprocessable Entity")));
    }
}
