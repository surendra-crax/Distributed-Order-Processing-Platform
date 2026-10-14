package com.platform.orderprocessing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Springdoc OpenAPI and Swagger UI documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Order Processing Platform API")
                        .description("Production-grade distributed order platform demonstrating concurrency control, "
                                + "transactional outbox publishing, idempotent Kafka consumers, and Redis caching.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Platform Engineering Team")
                                .url("https://github.com/surendra-crax/Distributed-Order-Processing-Platform"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
