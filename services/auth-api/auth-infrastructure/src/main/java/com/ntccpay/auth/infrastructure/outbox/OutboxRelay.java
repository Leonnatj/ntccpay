package com.ntccpay.auth.infrastructure.outbox;

import com.ntccpay.auth.infrastructure.persistence.OutboxEventEntity;
import com.ntccpay.auth.infrastructure.persistence.OutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Polls the transactional outbox (ADR 0005) and relays unpublished rows to
 * Kafka. At-least-once: a row is marked published only after Kafka acks the
 * send; on failure it stays unpublished and is retried on the next poll.
 * Consumers deduplicate by the deterministic event id.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxJpaRepository outbox;
    private final KafkaTemplate<Object, Object> kafka;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxJpaRepository outbox, KafkaTemplate<Object, Object> kafka, OutboxProperties properties) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ntccpay.outbox.relay-poll-millis:1000}")
    public void relayPendingEvents() {
        if (!properties.relayEnabled()) {
            return;
        }
        var page = outbox.findByPublishedAtIsNullOrderByOccurredAtAsc(
                PageRequest.of(0, properties.relayBatchSize()));
        page.getContent().forEach(this::publish);
    }

    private void publish(OutboxEventEntity event) {
        try {
            kafka.send(
                            properties.topic(),
                            event.getAggregateId().toString(),
                            event.getPayload())
                    .get(properties.relaySendTimeoutMillis(), TimeUnit.MILLISECONDS);
            var marked = outbox.markPublished(event.getId(), Instant.now());
            if (marked == 0) {
                log.warn("Outbox event {} already published by another poller; skipping", event.getId());
            } else {
                log.debug("Relayed outbox event {} to {}", event.getId(), properties.topic());
            }
        } catch (Exception e) {
            // Leave the row unpublished; the next poll retries it.
            log.warn("Outbox relay failed for event {}: {}", event.getId(), e.toString());
        }
    }
}