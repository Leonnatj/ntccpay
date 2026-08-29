package com.ntccpay.auth.domain.event;

import com.ntccpay.auth.domain.model.AuthorizationId;
import com.ntccpay.auth.domain.model.ReasonCode;

import java.time.Instant;

/** The authorization was declined, with a reason. */
public record AuthorizationDeclined(AuthorizationId authorizationId, ReasonCode reasonCode, Instant occurredAt)
        implements DomainEvent {
}
