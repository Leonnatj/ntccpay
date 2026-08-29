package com.ntccpay.auth.application.port.in;

/** Inbound command: everything needed to decide one authorization. */
public record AuthorizationCommand(String pan,
                                   long amountMinor,
                                   String currency,
                                   String merchant,
                                   String idempotencyKey) {
}
