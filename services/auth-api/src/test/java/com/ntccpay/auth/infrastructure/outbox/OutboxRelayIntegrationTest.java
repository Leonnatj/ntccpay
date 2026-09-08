package com.ntccpay.auth.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.AuthorizationId;
import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.IdempotencyKey;
import com.ntccpay.auth.domain.model.MerchantId;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.model.ReasonCode;
import com.ntccpay.auth.domain.service.DecisionResult;
import com.ntccpay.auth.infrastructure.persistence.JpaAuthorizationRepository;
import com.ntccpay.auth.infrastructure.persistence.OutboxEventEntity;
import com.ntccpay.auth.infrastructure.persistence.OutboxJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 outbox exit evidence: an authorization decision is written to
 * Postgres AND relayed to Kafka (real broker, Testcontainers) in the same
 * story, the payload matches the documented auths.v1 schema (masked PAN only),
 * and rows are marked published only after a successful send.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "ntccpay.security.api-keys.acme-corp=test-key-123",
        // keep the scheduler quiet so the test controls exactly when the relay runs
        "ntccpay.outbox.relay-poll-millis=60000"
})
class OutboxRelayIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.8.0"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void kafkaBootstrap(DynamicPropertyRegistry registry) {
        // Testcontainers' ConfluentKafkaContainer is the battle-tested broker for
        // integration tests; the compose stack runs the official apache/kafka image
        // (same Kafka protocol and same auths.v1 contract).
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private JpaAuthorizationRepository repository;

    @Autowired
    private OutboxJpaRepository outbox;

    @Autowired
    private OutboxRelay relay;

    @Test
    void decisionEventsAreRelayedToKafkaAndMarkedPublished() throws Exception {
        var approved = decided("k-approved", DecisionResult.approved());
        var declined = decided("k-declined", DecisionResult.declined(ReasonCode.CARD_BLOCKED));
        repository.save(approved);
        repository.save(declined);

        assertThat(unpublished()).hasSize(2);

        try (var consumer = consumerOn(kafka.getBootstrapServers())) {
            consumer.subscribe(List.of("auths.v1"));
            relay.relayPendingEvents();

            var records = awaitRecords(consumer, 2, Duration.ofSeconds(30));
            assertThat(records).hasSize(2);
            assertThat(records)
                    .map(ConsumerRecord::key)
                    .containsExactlyInAnyOrder(approved.id().value().toString(), declined.id().value().toString());

            for (var record : records) {
                // PCI: the masked form only; the raw PAN never reaches Kafka
                assertThat(record.value()).doesNotContain("4242424242424242");
                JsonNode json = MAPPER.readTree(record.value());
                assertThat(json.get("eventId").asText()).isNotBlank();
                assertThat(json.get("authorizationId").asText()).isEqualTo(record.key());
                assertThat(json.get("cardMasked").asText()).isEqualTo("****4242");
                assertThat(json.get("amountMinor").asLong()).isEqualTo(1000);
                assertThat(json.get("currency").asText()).isEqualTo("USD");
                assertThat(json.get("merchantId").asText()).isEqualTo("acme-corp");
                assertThat(json.get("occurredAt").asText()).isNotBlank();
                if ("AuthorizationApproved".equals(json.get("type").asText())) {
                    assertThat(json.get("decision").asText()).isEqualTo("APPROVED");
                    assertThat(json.hasNonNull("reasonCode")).isFalse();
                } else if ("AuthorizationDeclined".equals(json.get("type").asText())) {
                    assertThat(json.get("decision").asText()).isEqualTo("DECLINED");
                    assertThat(json.get("reasonCode").asText()).isEqualTo("CARD_BLOCKED");
                } else {
                    throw new AssertionError("unexpected event type " + json.get("type"));
                }
            }
        }

        // Marked published: the relay acknowledged before leaving the row pending.
        assertThat(unpublished()).isEmpty();
    }

    private Authorization decided(String key, DecisionResult result) {
        var card = new CardNumber("4242424242424242");
        var amount = new Money(1000, "USD");
        var merchant = new MerchantId("acme-corp");
        var authorization = Authorization.request(
                AuthorizationId.newId(), new IdempotencyKey(key),
                Authorization.fingerprintOf(card, amount, merchant), card, amount, merchant);
        authorization.decide(result);
        return authorization;
    }

    private List<OutboxEventEntity> unpublished() {
        return outbox.findByPublishedAtIsNullOrderByOccurredAtAsc(PageRequest.of(0, 50)).getContent();
    }

    private static KafkaConsumer<String, String> consumerOn(String bootstrapServers) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + System.nanoTime());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(props);
    }

    private static List<ConsumerRecord<String, String>> awaitRecords(
            KafkaConsumer<String, String> consumer, int expected, Duration timeout) {
        var deadline = System.currentTimeMillis() + timeout.toMillis();
        var records = new ArrayList<ConsumerRecord<String, String>>();
        while (records.size() < expected && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }
}