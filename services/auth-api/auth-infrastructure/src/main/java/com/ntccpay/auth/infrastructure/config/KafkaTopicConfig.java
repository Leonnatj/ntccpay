package com.ntccpay.auth.infrastructure.config;

import com.ntccpay.auth.infrastructure.outbox.OutboxProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the platform topics; Spring Boot's auto-configured {@code KafkaAdmin}
 * creates them on startup. Topics are the published language (docs/events/).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic authorizationEventsTopic(OutboxProperties properties) {
        return new NewTopic(properties.topic(), 1, (short) 1);
    }

    @Bean
    NewTopic captureEventsTopic() {
        return new NewTopic("captures.v1", 1, (short) 1);
    }
}