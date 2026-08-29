package com.ntccpay.auth.domain.event;

import com.ntccpay.auth.domain.model.AuthorizationId;

import java.time.Instant;

/** The authorization was approved. */
public record AuthorizationApproved(AuthorizationId authorizationId, Instant occurredAt) implements DomainEvent {
}
