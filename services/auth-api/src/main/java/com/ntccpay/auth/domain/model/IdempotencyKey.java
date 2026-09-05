package com.ntccpay.auth.domain.model;

/** Client-supplied key making a repeated request return the original decision. */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("idempotency key must be 1-100 characters");
        }
    }
}
