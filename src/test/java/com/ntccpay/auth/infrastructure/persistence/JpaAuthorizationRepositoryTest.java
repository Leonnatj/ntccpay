package com.ntccpay.auth.infrastructure.persistence;

import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.AuthorizationId;
import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.model.IdempotencyKey;
import com.ntccpay.auth.domain.model.MerchantId;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.service.DecisionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The port contract proven against real Postgres — not the in-memory fake:
 * roundtrip fidelity, the unique-key guarantee, and the Phase 2 exit
 * criterion that concurrent duplicate requests cannot double-insert.
 * The database, not application code, enforces the invariant.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuthorizationRepository.class)
class JpaAuthorizationRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JpaAuthorizationRepository repository;

    private Authorization approved(String key, AuthorizationId id) {
        var card = new CardNumber("4242424242424242");
        var amount = new Money(1000, "USD");
        var merchant = new MerchantId("acme-corp");
        var aggregate = Authorization.request(id, new IdempotencyKey(key),
                Authorization.fingerprintOf(card, amount, merchant), card, amount, merchant);
        aggregate.decide(DecisionResult.approved());
        return aggregate;
    }

    @Test
    void aSavedAuthorizationRoundTripsWithoutTheFullPan() {
        var original = approved("k-roundtrip", AuthorizationId.newId());
        repository.save(original);

        var restored = repository.findByIdempotencyKey(new IdempotencyKey("k-roundtrip")).orElseThrow();

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.decision()).isEqualTo(Decision.APPROVED);
        assertThat(restored.amount()).isEqualTo(original.amount());
        assertThat(restored.merchant()).isEqualTo(original.merchant());
        assertThat(restored.requestFingerprint()).isEqualTo(original.requestFingerprint());
        // PCI: only the masked card survives persistence — the raw PAN is unreachable
        assertThat(restored.cardNumber().masked()).isEqualTo("****4242");
        assertThat(restored.cardNumber().isMaskedReference()).isTrue();
        assertThatThrownBy(restored.cardNumber()::raw)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never stored");
    }

    @Test
    void aSecondSaveForTheSameKeyIsRejected() {
        repository.save(approved("k-duplicate", AuthorizationId.newId()));

        assertThatThrownBy(() -> repository.save(approved("k-duplicate", AuthorizationId.newId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate idempotency key");
        // The row-count guarantee ("exactly one row per key") is proven by
        // concurrentRequestsSharingAKeyCannotDoubleInsert, where each save runs
        // in its own session and transaction exactly as in production — within
        // this shared test session Hibernate's persistence-context check fires
        // before the database constraint, leaving the count meaningless here.
    }

    @Test
    void concurrentRequestsSharingAKeyCannotDoubleInsert() throws Exception {
        var first = approved("k-race", AuthorizationId.newId());
        var second = approved("k-race", AuthorizationId.newId());
        var start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var f1 = pool.submit(saveWhen(start, repository, first));
            var f2 = pool.submit(saveWhen(start, repository, second));

            start.countDown();
            var successes = 0;
            var rejected = 0;
            for (var future : new Future<?>[]{f1, f2}) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("duplicate idempotency key");
                    rejected++;
                }
            }
            // exactly one of the two racing inserts wins — never both, never neither
            assertThat(successes).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByIdempotencyKey(new IdempotencyKey("k-race"))).isPresent();
    }

    private static Runnable saveWhen(CountDownLatch start, JpaAuthorizationRepository repository,
                                     Authorization authorization) {
        return () -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            repository.save(authorization);
        };
    }
}
