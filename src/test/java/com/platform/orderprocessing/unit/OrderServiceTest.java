package com.platform.orderprocessing.unit;

import com.platform.orderprocessing.common.error.InvalidOrderStateException;
import com.platform.orderprocessing.order.domain.Order;
import com.platform.orderprocessing.order.domain.OrderStatus;
import com.platform.orderprocessing.order.dto.CreateOrderRequest;
import com.platform.orderprocessing.order.dto.OrderItemRequest;
import com.platform.orderprocessing.order.dto.OrderResponse;
import com.platform.orderprocessing.order.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("Should accurately calculate total price on server using BigDecimal without client input")
    void testOrderCreationCalculatesServerSideTotals() {
        // TECH-KEYB-01 is seeded with price 79.99
        // TECH-MOUS-02 is seeded with price 49.99
        CreateOrderRequest request = new CreateOrderRequest(
                "CUST-100",
                "customer@example.com",
                List.of(
                        new OrderItemRequest("TECH-KEYB-01", 2), // 2 * 79.99 = 159.98
                        new OrderItemRequest("TECH-MOUS-02", 1)  // 1 * 49.99 = 49.99
                ),
                "USD"
        );

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.orderNumber()).startsWith("ORD-");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        // Total expected = 159.98 + 49.99 = 209.97
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("209.97"));
        assertThat(response.items()).hasSize(2);
    }

    @Test
    @DisplayName("Should strictly enforce allowable order state transitions")
    void testOrderStateTransitions() {
        Order order = new Order(null, "ORD-TEST-1", "CUST-1", "cust@test.com", BigDecimal.TEN, "USD");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Valid transition PENDING -> CONFIRMED
        order.transitionTo(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Valid transition CONFIRMED -> PROCESSING
        order.transitionTo(OrderStatus.PROCESSING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        // Valid transition PROCESSING -> COMPLETED
        order.transitionTo(OrderStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Disallowed: terminal state COMPLETED cannot transition to PENDING
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("cannot transition from COMPLETED to PENDING");
    }
}
