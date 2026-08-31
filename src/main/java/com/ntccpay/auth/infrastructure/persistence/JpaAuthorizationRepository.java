package com.ntccpay.auth.infrastructure.persistence;

import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.AuthorizationId;
import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.model.IdempotencyKey;
import com.ntccpay.auth.domain.model.MerchantId;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.model.ReasonCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * JPA adapter for the {@link AuthorizationRepository} port. Persistence model
 * &ne; domain model: this is the only place domain aggregates and JPA entities
 * meet. The write path is append-only (inserts only, no updates to decisions).
 */
@Component
public class JpaAuthorizationRepository implements AuthorizationRepository {

    private final AuthorizationJpaRepository jpa;
    private final TransactionTemplate transactions;

    public JpaAuthorizationRepository(AuthorizationJpaRepository jpa, PlatformTransactionManager transactionManager) {
        this.jpa = jpa;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void save(Authorization authorization) {
        try {
            transactions.executeWithoutResult(status ->
                    jpa.saveAndFlush(AuthorizationEntity.fromDomain(authorization)));
        } catch (DataIntegrityViolationException e) {
            // The PRIMARY KEY on idempotency_keys is the concurrency-proof guarantee:
            // two racing inserts cannot both commit. Translated to the port's contract.
            throw new IllegalStateException(
                    "duplicate idempotency key '" + authorization.idempotencyKey().value() + "'", e);
        }
    }

    @Override
    public Optional<Authorization> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return jpa.findByIdempotencyKeyValue(idempotencyKey.value()).map(AuthorizationEntity::toDomain);
    }

    @Override
    public long count() {
        return jpa.count();
    }
}
