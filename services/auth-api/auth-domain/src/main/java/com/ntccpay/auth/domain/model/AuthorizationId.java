package com.ntccpay.auth.domain.model;

import java.util.UUID;

public record AuthorizationId(UUID value) {

    public AuthorizationId {
        if (value == null) {
            throw new IllegalArgumentException("authorization id is required");
        }
    }

    public static AuthorizationId newId() {
        return new AuthorizationId(UUID.randomUUID());
    }
}
