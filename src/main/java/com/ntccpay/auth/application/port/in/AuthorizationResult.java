package com.ntccpay.auth.application.port.in;

import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.model.ReasonCode;

import java.time.Instant;
import java.util.UUID;

/** Outbound result of the use case. Never leaks the domain aggregate. */
public record AuthorizationResult(UUID authorizationId,
                                  Decision decision,
                                  ReasonCode reasonCode,
                                  String maskedPan,
                                  Instant decidedAt,
                                  boolean replayed) {

    public static AuthorizationResult of(com.ntccpay.auth.domain.model.Authorization authorization, boolean replayed) {
        return new AuthorizationResult(
                authorization.id().value(),
                authorization.decision(),
                authorization.reasonCode(),
                authorization.cardNumber().masked(),
                authorization.decidedAt(),
                replayed);
    }

    public static AuthorizationResult replayOf(com.ntccpay.auth.domain.model.Authorization authorization) {
        return of(authorization, true);
    }
}
