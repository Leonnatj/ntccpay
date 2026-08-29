package com.ntccpay.auth.domain.model;

/** Bank Identification Number: the leading six digits of a PAN. */
public record Bin(String value) {

    public Bin {
        if (value == null || !value.matches("\\d{6}")) {
            throw new IllegalArgumentException("BIN must be 6 digits");
        }
    }
}
