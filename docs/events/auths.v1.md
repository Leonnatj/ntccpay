# `auths.v1` — authorization decision events

The published language between bounded contexts (ADR 0004: the only contract
between services). Producers: auth-api's transactional outbox. Consumers in
Phase 3: ledger-service (M2), notification-service (M3).

- Topic: `auths.v1`
- Message key: `authorizationId` (UUID string) — same key for all events of one
  authorization keeps ordering-per-aggregate.
- Delivery: **at-least-once**. `eventId` is deterministic
  (`uuid5(authorizationId:type:occurredAt)`) so consumers deduplicate safely.
- PCI: the payload carries only the masked PAN (`****4242`); the full PAN
  never leaves auth-api.

## Envelope

```json
{
  "eventId": "2f3a1f10-...",
  "authorizationId": "8e1c...-uuid",
  "type": "AuthorizationApproved | AuthorizationDeclined",
  "cardMasked": "****4242",
  "amountMinor": 1000,
  "currency": "USD",
  "merchantId": "acme-corp",
  "decision": "APPROVED | DECLINED",
  "reasonCode": "CARD_BLOCKED | CURRENCY_NOT_SUPPORTED | AMOUNT_EXCEEDS_LIMIT | INVALID_PAN",
  "occurredAt": "2026-09-05T10:15:30.123456Z"
}
```

| Field | Type | Meaning |
|---|---|---|
| `eventId` | UUID | Deterministic id of this event; the consumer's dedup key |
| `authorizationId` | UUID | Aggregate id; also the Kafka message key |
| `type` | string | Event sub-type; distinguishes approved from declined |
| `cardMasked` | string | Last 4 digits only (`****4242`) — never the full PAN |
| `amountMinor` | integer | Amount in minor units (cents); never floats |
| `currency` | string | ISO 4217 code |
| `merchantId` | string | The requesting merchant |
| `decision` | string | `APPROVED` or `DECLINED` |
| `reasonCode` | string? | Present only when `decision = DECLINED` |
| `occurredAt` | ISO-8601 | When the decision was made (UTC) |

## Versioning

The `v1` in the topic name is the schema version. A breaking change to the
envelope is a new topic (`auths.v2`), never an edit in place: consumers commit
to the schema at their level, and the same release may run both producers and
consumers on overlapping versions during roll-out.

The Java mirror of this document (write side) is
`AuthorizationEventV1` in auth-api's `infrastructure.outbox` package.