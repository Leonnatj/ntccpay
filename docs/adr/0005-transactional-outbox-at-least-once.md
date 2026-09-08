# ADR 0005: Transactional outbox + at-least-once delivery for auth events

- Status: accepted
- Date: 2026-09-05
- Deciders: project owner
- Related: ADR 0004 (services publish via versioned JSON events)

## Context

auth-api must relay authorization decisions to Kafka so ledger, notification,
fraud, and settlement services can react asynchronously. The naive pattern —
"write the decision row, then publish to Kafka" — is two independent steps:
a crash between them loses an event, and the auth latency budget forbids a
synchronous Kafka round-trip on the critical path.

Options considered:

1. **Publish-then-write** — send to Kafka before committing the DB write.
   Broken: a commit failure emits an event that never happened.
2. **Write-then-publish (naive)** — commit, then send. Broken: a crash or
   timeout between the two loses the event (or duplicates it).
3. **Transactional outbox (chosen)** — persist the aggregate AND its domain
   events to an `outbox_events` table in ONE database transaction; a poller
   relays unpublished rows to Kafka afterwards.
4. **Change data capture (Debezium)** — emit events from the Postgres write
   log. Rejected for now: another infra component to run and learn, opaque
   schema coupling; the outbox table is the same guaranteed ordering with a
   tenth of the moving parts.

## Decision

Use the transactional outbox:

- `V2` migration adds `outbox_events` (`id`, `aggregate_id`, `event_type`,
  `payload` JSONB, `occurred_at`, nullable `published_at`).
- `JpaAuthorizationRepository.save()` writes the authorization AND its
  decision domain events in the same `TransactionTemplate` — all-commit or
  all-rollback, so a decision row without its event is impossible.
- `OutboxRelay` (`@Scheduled`) polls `published_at IS NULL` rows, publishes
  each payload to `auths.v1` (key = `aggregate_id`), then marks the row
  published. A row is marked only after Kafka acknowledges the send.
- Delivery is **at-least-once**: a crash between publish and mark re-sends on
  restart. Consumers therefore deduplicate by deterministic `eventId`
  (see docs/events/auths.v1.md).
- `AuthorizationRequested` is consumed internally only — only the decision
  events are relayed.

## Rationale

- The auth transaction stays a single DB write; the Kafka hop happens off the
  critical path (p99 budget untouched).
- No event is ever lost: the outbox row IS the source of truth until acked.
- At-least-once + consumer dedup (processed-events table, M2) is the classic
  trade: dedup is cheap and local, while exactly-once needs distributed
  transactions or idempotent side effects — heavier and slower.

## Consequences

- Multiple pollers would double-send; `markPublished` guards with
  `published_at IS NULL` and consumers dedup regardless. The default
  single-auth-api deployment makes this theoretical.
- Outbox rows grow unboundedly; a retention job (delete rows older than N days
  after `published_at`) is a follow-up, not a Phase 3 requirement.
- The relay retries forever on Kafka outage (rows stay unpublished) — the
  intended behaviour for "restart loses no events".