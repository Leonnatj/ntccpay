package com.ntccpay.auth.domain.model;

/**
 * Money as minor units (cents) plus an ISO 4217 currency code.
 * Never a float. Never a double. Money correctness starts here.
 */
public record Money(long minorUnits, String currencyCode) {

    public Money {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        if (currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
        }
    }
}
