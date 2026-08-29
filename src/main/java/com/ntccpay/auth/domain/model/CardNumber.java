package com.ntccpay.auth.domain.model;

/**
 * The Primary Account Number. Toxic data: {@link #toString()} is masked by
 * construction, so accidental logging cannot leak the full PAN.
 */
public record CardNumber(String value) {

    private static final String DIGITS_13_TO_19 = "\\d{13,19}";

    public CardNumber {
        if (value == null || !value.matches(DIGITS_13_TO_19)) {
            throw new IllegalArgumentException("PAN must be 13-19 digits");
        }
    }

    /** Standard Luhn checksum validation. */
    public boolean luhnValid() {
        return isValidLuhn(value);
    }

    /** The first six digits identifying the issuing institution. */
    public Bin bin() {
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

    /** Raw access for fingerprinting and rule checks. Never log the result. */
    public String raw() {
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
