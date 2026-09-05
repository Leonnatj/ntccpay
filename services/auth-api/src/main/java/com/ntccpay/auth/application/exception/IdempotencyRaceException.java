package com.ntccpay.auth.application.exception;

import com.ntccpay.auth.domain.model.IdempotencyKey;

/**
 * Another request with the same idempotency key committed first — our insert
 * lost the PRIMARY KEY race (the lookup found nothing, but by insert time the
 * key already existed). Not an HTTP error by itself: the use case resolves the
 * race by re-reading the stored decision — a replay for an identical request,
 * a {@link IdempotencyConflictException} for a different one.
 */
public class IdempotencyRaceException extends RuntimeException {

    public IdempotencyRaceException(IdempotencyKey key) {
        super("another request with idempotency key '" + key.value() + "' committed first");
    }
}
