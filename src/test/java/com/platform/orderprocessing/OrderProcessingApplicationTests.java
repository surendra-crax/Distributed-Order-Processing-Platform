package com.platform.orderprocessing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderProcessingApplicationTests {

    @Test
    @DisplayName("Should successfully load application context with H2 and Flyway migrations")
    void contextLoads() {
        // Verifies Spring context, Flyway migration scripts, and all baseline beans bootstrap properly
    }
}
