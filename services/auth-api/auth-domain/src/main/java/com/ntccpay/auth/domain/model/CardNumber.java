package com.ntccpay.auth.domain.model;

/**
 * The Primary Account Number. Toxic data: {@link #toString()} is masked by
 * construction, so accidental logging cannot leak the full PAN.
 */
public record CardNumber(String value) {

    private static final String DIGITS_13_TO_19 = "\\d{13,19}";
    private static final String MASKED_REFERENCE = "\\*{4}\\d{4}";

    public CardNumber {
        if (value == null
                || (!value.matches(DIGITS_13_TO_19) && !value.matches(MASKED_REFERENCE))) {
            throw new IllegalArgumentException("PAN must be 13-19 digits");
        }
    }

    /**
     * Display-only card reference for aggregates rehydrated from storage: the
     * full PAN was never persisted (PCI), so only the masked form exists.
     */
    public static CardNumber maskedReference(String masked) {
        return new CardNumber(masked);
    }

    /** True when this instance carries only the masked form (persisted history). */
    public boolean isMaskedReference() {
        return value.matches(MASKED_REFERENCE);
    }

    /** Standard Luhn checksum validation. */
    public boolean luhnValid() {
        if (isMaskedReference()) {
            throw new IllegalStateException("Luhn needs the full PAN; masked references carry none");
        }
        return isValidLuhn(value);
    }

    /** The first six digits identifying the issuing institution. */
    public Bin bin() {
        if (isMaskedReference()) {
            throw new IllegalStateException("BIN needs the full PAN; masked references carry none");
        }
        return new Bin(value.substring(0, 6));
    }

    /** Last four digits only, suitable for display and logs. */
    public String masked() {
        return "****" + value.substring(value.length() - 4);
    }

    /** Never logs the full PAN. */
    @Override
    public String toString() {
        return masked();
    }

    /**
     * Raw access for fingerprinting and rule checks. Never log the result.
     * Refuses on masked references: the PAN was never stored, so it cannot leak.
     */
    public String raw() {
        if (isMaskedReference()) {
            throw new IllegalStateException("full PAN was never stored; only the masked reference is available");
        }
        return value;
    }

    public static boolean isValidLuhn(String pan) {
        int sum = 0;
        boolean doubleNext = false;
        for (int i = pan.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(pan.charAt(i));
            if (doubleNext) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return sum % 10 == 0;
    }
}
