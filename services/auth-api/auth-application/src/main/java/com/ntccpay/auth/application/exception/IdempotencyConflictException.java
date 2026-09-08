package com.ntccpay.auth.application.exception;

import com.ntccpay.auth.domain.model.IdempotencyKey;

/** Same idempotency key, different request contents. HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(IdempotencyKey key) {
        super("idempotency key '" + key.value() + "' was already used for a different request");
    }
}
