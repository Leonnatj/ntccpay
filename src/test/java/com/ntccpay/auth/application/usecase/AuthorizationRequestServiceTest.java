package com.ntccpay.auth.application.usecase;

import com.ntccpay.auth.application.exception.IdempotencyConflictException;
import com.ntccpay.auth.application.port.in.AuthorizationCommand;
import com.ntccpay.auth.application.port.in.AuthorizationResult;
import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.service.AuthorizationRuleEngine;
import com.ntccpay.auth.testing.InMemoryAuthorizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationRequestServiceTest {

    private InMemoryAuthorizationRepository repository;
    private AuthorizationRequestService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuthorizationRepository();
        service = new AuthorizationRequestService(repository, new AuthorizationRuleEngine(
                Set.of("USD", "EUR", "GBP"), 50_000, Set.of(), Set.of()));
    }

    private AuthorizationCommand command(long amount, String key) {
        return new AuthorizationCommand("4242424242424242", amount, "USD", "acme-corp", key);
    }

    @Test
    void aRequestCreatesAndDecidesAnAuthorization() {
        var result = service.handle(command(1000, "abc-123"));

        assertThat(result.decision()).isEqualTo(Decision.APPROVED);
        assertThat(result.replayed()).isFalse();
        assertThat(result.maskedPan()).isEqualTo("****4242");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheOriginalDecision() {
        var first = service.handle(command(1000, "abc-123"));
        var second = service.handle(command(1000, "abc-123"));

        assertThat(second.replayed()).isTrue();
        assertThat(second.authorizationId()).isEqualTo(first.authorizationId());
        assertThat(second.decision()).isEqualTo(first.decision());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentRequestIsAConflict() {
        service.handle(command(1000, "abc-123"));

        assertThatThrownBy(() -> service.handle(command(2000, "abc-123")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void differentKeysCreateIndependentAuthorizations() {
        service.handle(command(1000, "abc-123"));
        service.handle(command(1000, "def-456"));

        assertThat(repository.count()).isEqualTo(2);
    }
}
