package com.ntccpay.auth.domain.event;

import com.ntccpay.auth.domain.model.AuthorizationId;

import java.time.Instant;

/** An authorization request was received and is being decided. */
public record AuthorizationRequested(AuthorizationId authorizationId, Instant occurredAt) implements DomainEvent {
}
