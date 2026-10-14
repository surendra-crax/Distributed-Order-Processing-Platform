package com.platform.orderprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Distributed Order Processing Platform.
 * Bootstraps the modular monolith application including the Order API,
 * Inventory Engine, Transactional Outbox Relay, and Event Consumers.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class OrderProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingApplication.class, args);
    }
}
