package com.ntccpay.auth.infrastructure.persistence;

import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.IdempotencyKey;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 in-memory store: the decision path has zero I/O. The unique-key
 * semantics here mirror the DB unique constraint that replaces it in Phase 2.
 */
@Component
public final class InMemoryAuthorizationRepository implements AuthorizationRepository {

    private final Map<IdempotencyKey, Authorization> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public void save(Authorization authorization) {
        var previous = byIdempotencyKey.putIfAbsent(authorization.idempotencyKey(), authorization);
        if (previous != null && !previous.id().equals(authorization.id())) {
            throw new IllegalStateException(
                    "duplicate idempotency key '" + authorization.idempotencyKey().value() + "'");
        }
    }

    @Override
    public Optional<Authorization> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public long count() {
        return byIdempotencyKey.size();
    }
}
