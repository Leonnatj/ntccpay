package com.ntccpay.auth.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Transactional-outbox / relay behaviour. Picked up by @ConfigurationPropertiesScan. */
@ConfigurationProperties(prefix = "ntccpay.outbox")
public record OutboxProperties(String topic,
                              Boolean relayEnabled,
                              long relayPollMillis,
                              int relayBatchSize,
                              long relaySendTimeoutMillis) {

    public OutboxProperties {
        topic = topic == null || topic.isBlank() ? "auths.v1" : topic;
        relayEnabled = relayEnabled == null || relayEnabled; // default on; tests opt out
        relayPollMillis = relayPollMillis <= 0 ? 1000 : relayPollMillis;
        relayBatchSize = relayBatchSize <= 0 ? 50 : relayBatchSize;
        relaySendTimeoutMillis = relaySendTimeoutMillis <= 0 ? 5000 : relaySendTimeoutMillis;
    }
}