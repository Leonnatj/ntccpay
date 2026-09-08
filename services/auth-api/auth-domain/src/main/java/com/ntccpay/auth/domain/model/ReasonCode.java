package com.ntccpay.auth.domain.model;

/** The machine-readable why behind a decline. */
public enum ReasonCode {
    INVALID_PAN,
    CARD_BLOCKED,
    CURRENCY_NOT_SUPPORTED,
    AMOUNT_EXCEEDS_LIMIT
}
