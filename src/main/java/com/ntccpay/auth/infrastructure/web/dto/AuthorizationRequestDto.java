package com.ntccpay.auth.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Inbound translation layer. Validated at the boundary; never reaches the domain as-is.
 * Test card data only.
 */
public record AuthorizationRequestDto(
        @NotBlank
        @Pattern(regexp = "\\d{13,19}", message = "PAN must be 13-19 digits")
        String pan,

        @Positive
        long amountMinor,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency,

        @NotBlank
        String merchant) {
}
