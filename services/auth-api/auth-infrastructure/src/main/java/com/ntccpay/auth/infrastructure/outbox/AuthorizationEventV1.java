package com.ntccpay.auth.infrastructure.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * The {@code auths.v1} published-language document (docs/events/auths.v1.md).
 * Jacksons into the exact JSON shape consumers commit to (ledger-service,
 * notification-service). PCI by construction: only the masked PAN ever appears.
 */
public record AuthorizationEventV1(UUID eventId,
                                   UUID authorizationId,
                                   String type,
                                   String cardMasked,
                                   long amountMinor,
                                   String currency,
                                   String merchantId,
                                   String decision,
                                   String reasonCode,
                                   Instant occurredAt) {
}