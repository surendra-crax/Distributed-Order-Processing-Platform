package com.platform.orderprocessing.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload for creating a customer order.
 * Client does NOT supply total amounts — all calculations are performed server-side with BigDecimal.
 */
public record CreateOrderRequest(
        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be a valid email address")
        String customerEmail,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequest> items,

        String currency
) {}
