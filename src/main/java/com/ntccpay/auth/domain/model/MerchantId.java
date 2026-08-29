package com.ntccpay.auth.domain.model;

/** The party requesting the payment. */
public record MerchantId(String value) {

    public MerchantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("merchant id is required");
        }
    }
}
