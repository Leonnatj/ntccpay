-- Phase 3 (ADR 0005): transactional outbox. The Authorization decision and
-- its decision domain events are persisted in ONE transaction; a poller
-- (OutboxRelay) relays rows where published_at IS NULL to Kafka. Payload is a
-- plain TEXT column: the relay forwards stored bytes verbatim and consumers
-- parse the documented auths.v1 JSON, so the DB needs no json semantics.
CREATE TABLE outbox_events (
    id              UUID        PRIMARY KEY,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         TEXT        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_id);