package com.ntccpay.auth.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntccpay.auth.domain.event.AuthorizationApproved;
import com.ntccpay.auth.domain.event.AuthorizationDeclined;
import com.ntccpay.auth.domain.event.DomainEvent;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.infrastructure.outbox.AuthorizationEventV1;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model of one transactional-outbox row (ADR 0005): the versioned
 * {@code auths.v1} payload is written here in the SAME database transaction as
 * the Authorization aggregate, so a decision row without its event is
 * impossible. The {@link OutboxRelay} publishes rows where {@code published_at}
 * is null and marks them afterwards.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity implements Persistable<UUID> {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected OutboxEventEntity() {
        // for JPA
    }

    private OutboxEventEntity(UUID id, UUID aggregateId, String eventType, String payload, Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
    }

    /**
     * Maps one raised decision event of the aggregate to an outbox row.
     * Returns null for the internal-only {@code AuthorizationRequested} event —
     * only decisions are relayed to Kafka. The payload is serialized here, once,
     * so the relay simply forwards stored bytes (schema frozen at write time).
     */
    public static OutboxEventEntity fromDecisionEvent(Authorization authorization, DomainEvent event) {
        var owner = authorization.id().value();
        return switch (event) {
            case AuthorizationApproved approved -> row(owner, approved.occurredAt(), "AuthorizationApproved",
                    new AuthorizationEventV1(
                            eventId(owner, "AuthorizationApproved", approved.occurredAt()),
                            owner, "AuthorizationApproved",
                            authorization.cardNumber().masked(),
                            authorization.amount().minorUnits(),
                            authorization.amount().currencyCode(),
                            authorization.merchant().value(),
                            Decision.APPROVED.name(), null, approved.occurredAt()));
            case AuthorizationDeclined declined -> row(owner, declined.occurredAt(), "AuthorizationDeclined",
                    new AuthorizationEventV1(
                            eventId(owner, "AuthorizationDeclined", declined.occurredAt()),
                            owner, "AuthorizationDeclined",
                            authorization.cardNumber().masked(),
                            authorization.amount().minorUnits(),
                            authorization.amount().currencyCode(),
                            authorization.merchant().value(),
                            Decision.DECLINED.name(), declined.reasonCode().name(), declined.occurredAt()));
            default -> null;
        };
    }

    /** Deterministic across retries: the consumers' dedup key (docs/events/auths.v1.md). */
    static UUID eventId(UUID aggregateId, String type, Instant occurredAt) {
        return UUID.nameUUIDFromBytes(
                (aggregateId + ":" + type + ":" + occurredAt.toEpochMilli()).getBytes(StandardCharsets.UTF_8));
    }

    private static OutboxEventEntity row(UUID owner, Instant occurredAt, String type, AuthorizationEventV1 v1) {
        return new OutboxEventEntity(eventId(owner, type, occurredAt), owner, type, toJson(v1), occurredAt);
    }

    private static String toJson(AuthorizationEventV1 v1) {
        try {
            return JSON.writeValueAsString(v1);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize auths.v1 payload", e);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}