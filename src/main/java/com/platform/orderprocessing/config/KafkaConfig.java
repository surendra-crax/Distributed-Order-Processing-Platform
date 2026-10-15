package com.platform.orderprocessing.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration establishing topic definitions, consumer error handlers,
 * exponential retry policies, and dead-letter topic (DLT) routing.
 */
@Configuration
@org.springframework.context.annotation.Profile("!local")
public class KafkaConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${platform.kafka.topics.orders:order.events}")
    private String ordersTopic;

    @Value("${platform.kafka.topics.orders-dlt:order.events.DLT}")
    private String ordersDltTopic;

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public NewTopic ordersTopicDefinition() {
        return TopicBuilder.name(ordersTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public NewTopic ordersDltTopicDefinition() {
        return TopicBuilder.name(ordersDltTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    LOG.error("Retries exhausted for record with key '{}' on topic '{}'. Routing to DLT topic '{}'. Reason: {}",
                            record.key(), record.topic(), ordersDltTopic, ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(ordersDltTopic, record.partition());
                });

        // 3 retry attempts with 1000ms delay between attempts before routing to Dead Letter Topic
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
