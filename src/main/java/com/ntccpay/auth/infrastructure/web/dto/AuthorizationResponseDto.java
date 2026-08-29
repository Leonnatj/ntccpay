package com.ntccpay.auth.infrastructure.web.dto;

import com.ntccpay.auth.application.port.in.AuthorizationResult;

import java.time.Instant;
import java.util.UUID;

/** Outbound response. The PAN appears masked only - by construction. */
public record AuthorizationResponseDto(UUID authorizationId,
                                       String decision,
                                       String reasonCode,
                                       String maskedPan,
                                       Instant decidedAt,
                                       boolean replayed) {

    public static AuthorizationResponseDto from(AuthorizationResult result) {
        return new AuthorizationResponseDto(
                result.authorizationId(),
                result.decision().name(),
                result.reasonCode() == null ? null : result.reasonCode().name(),
                result.maskedPan(),
                result.decidedAt(),
                result.replayed());
    }
}
