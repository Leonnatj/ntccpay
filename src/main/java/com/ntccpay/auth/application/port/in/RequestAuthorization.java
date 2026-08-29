package com.ntccpay.auth.application.port.in;

/** Use case: decide one authorization request, idempotently. */
public interface RequestAuthorization {

    AuthorizationResult handle(AuthorizationCommand command);
}
