package com.ntccpay.auth.testing;

import com.ntccpay.auth.application.exception.IdempotencyRaceException;
import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.IdempotencyKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test fixture: the Phase 1 in-memory store. Deliberately lives in test
 * sources so the real Postgres adapter is the only repository on production
 * code paths — unit and Cucumber tests stay container-free, while the JPA
 * adapter is proven against real Postgres by JpaAuthorizationRepositoryTest.
 *
 * Not a Spring bean on purpose: @SpringBootTest component-scans com.ntccpay.auth,
 * and a second AuthorizationRepository bean here would make the context ambiguous.
 *
 * Non-final on purpose: tests subclass it to simulate race conditions (e.g. a save
 * that throws {@code IdempotencyRaceException} because a concurrent twin committed first).
 */
public class InMemoryAuthorizationRepository implements AuthorizationRepository {

    private final Map<IdempotencyKey, Authorization> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public void save(Authorization authorization) {
        var previous = byIdempotencyKey.putIfAbsent(authorization.idempotencyKey(), authorization);
        if (previous != null && !previous.id().equals(authorization.id())) {
            throw new IdempotencyRaceException(authorization.idempotencyKey());
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
