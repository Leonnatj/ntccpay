package com.ntccpay.auth.domain.model;

import com.ntccpay.auth.domain.event.AuthorizationApproved;
import com.ntccpay.auth.domain.event.AuthorizationDeclined;
import com.ntccpay.auth.domain.event.AuthorizationRequested;
import com.ntccpay.auth.domain.service.DecisionResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationTest {

    private final IdempotencyKey key = new IdempotencyKey("abc-123");
    private final CardNumber card = new CardNumber("4242424242424242");
    private final Money amount = new Money(1000, "USD");
    private final MerchantId merchant = new MerchantId("acme-corp");

    @Test
    void aNewAuthorizationRaisesTheRequestedEvent() {
        var authorization = Authorization.request(
                AuthorizationId.newId(), key,
                Authorization.fingerprintOf(card, amount, merchant),
                card, amount, merchant);

        assertThat(authorization.decision()).isNull();
        assertThat(authorization.domainEvents()).hasSize(1);
        assertThat(authorization.domainEvents().get(0)).isInstanceOf(AuthorizationRequested.class);
    }

    @Test
    void decidingRaisesTheApprovedEvent() {
        var authorization = authorizedWith(DecisionResult.approved());

        assertThat(authorization.decision()).isEqualTo(Decision.APPROVED);
        assertThat(authorization.reasonCode()).isNull();
        assertThat(authorization.decidedAt()).isNotNull();
        assertThat(authorization.domainEvents()).hasSize(2);
        assertThat(authorization.domainEvents().get(1)).isInstanceOf(AuthorizationApproved.class);
    }

    @Test
    void decidingDeclinedCarriesTheReasonCode() {
        var authorization = authorizedWith(DecisionResult.declined(ReasonCode.CARD_BLOCKED));

        assertThat(authorization.decision()).isEqualTo(Decision.DECLINED);
        assertThat(authorization.reasonCode()).isEqualTo(ReasonCode.CARD_BLOCKED);
        assertThat(authorization.domainEvents().get(1)).isInstanceOf(AuthorizationDeclined.class);
    }

    @Test
    void aDecisionOnceMadeIsImmutable() {
        var authorization = authorizedWith(DecisionResult.approved());

        assertThatThrownBy(() -> authorization.decide(DecisionResult.declined(ReasonCode.CARD_BLOCKED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");
        assertThat(authorization.decision()).isEqualTo(Decision.APPROVED);
    }

    @Test
    void theSameRequestHasTheSameFingerprint() {
        var first = Authorization.fingerprintOf(card, amount, merchant);
        var second = Authorization.fingerprintOf(
                new CardNumber("4242424242424242"), new Money(1000, "USD"), new MerchantId("acme-corp"));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void aDifferentRequestHasADifferentFingerprint() {
        var sameCardDifferentAmount = Authorization.fingerprintOf(
                card, new Money(2000, "USD"), merchant);
        assertThat(Authorization.fingerprintOf(card, amount, merchant))
                .isNotEqualTo(sameCardDifferentAmount);
    }

    @Test
    void aDecisionResultIsAlwaysDecided() {
        var authorization = Authorization.request(
                new AuthorizationId(UUID.randomUUID()), key,
                Authorization.fingerprintOf(card, amount, merchant),
                card, amount, merchant);
        assertThatThrownBy(() -> authorization.decide(null))
                .isInstanceOf(NullPointerException.class);
    }

    private Authorization authorizedWith(DecisionResult result) {
        var authorization = Authorization.request(
                AuthorizationId.newId(), key,
                Authorization.fingerprintOf(card, amount, merchant),
                card, amount, merchant);
        authorization.decide(result);
        return authorization;
    }
}
