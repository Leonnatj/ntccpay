package com.ntccpay.auth.domain.service;

import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.model.ReasonCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationRuleEngineTest {

    private final AuthorizationRuleEngine engine = new AuthorizationRuleEngine(
            Set.of("USD", "EUR", "GBP"),
            50_000,
            Set.of("4000000000000002"),
            Set.of("400100"));

    @Test
    void aNormalRequestIsApproved() {
        var result = engine.evaluate(
                new CardNumber("4242424242424242"), new Money(1000, "USD"));

        assertThat(result.decision()).isEqualTo(com.ntccpay.auth.domain.model.Decision.APPROVED);
        assertThat(result.reasonCode()).isNull();
    }

    @Test
    void aPanFailingLuhnIsDeclined() {
        var result = engine.evaluate(
                new CardNumber("4242424242424241"), new Money(1000, "USD"));

        assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVALID_PAN);
    }

    @Test
    void aBlocklistedCardIsDeclined() {
        var result = engine.evaluate(
                new CardNumber("4000000000000002"), new Money(1000, "USD"));

        assertThat(result.reasonCode()).isEqualTo(ReasonCode.CARD_BLOCKED);
    }

    @Test
    void aCardFromABlockedBinIsDeclined() {
        // passes Luhn, not card-blocklisted, but its BIN is blocked
        var result = engine.evaluate(
                new CardNumber("4001001234567898"), new Money(1000, "USD"));

        assertThat(result.reasonCode()).isEqualTo(ReasonCode.CARD_BLOCKED);
    }

    @Test
    void anUnsupportedCurrencyIsDeclined() {
        var result = engine.evaluate(
                new CardNumber("4242424242424242"), new Money(1000, "JPY"));

        assertThat(result.reasonCode()).isEqualTo(ReasonCode.CURRENCY_NOT_SUPPORTED);
    }

    @Test
    void anAmountAboveTheLimitIsDeclined() {
        var result = engine.evaluate(
                new CardNumber("4242424242424242"), new Money(50_001, "USD"));

        assertThat(result.reasonCode()).isEqualTo(ReasonCode.AMOUNT_EXCEEDS_LIMIT);
    }

    @Test
    void theFirstFailingRuleWins() {
        // invalid PAN + unsupported currency + over the limit: only INVALID_PAN may surface
        var result = engine.evaluate(
                new CardNumber("4242424242424241"), new Money(99_999, "JPY"));

        assertThat(result.reasonCode()).isEqualTo(ReasonCode.INVALID_PAN);

        // valid PAN, blocked card, over the limit: CARD_BLOCKED wins
        var blockedAndOverLimit = engine.evaluate(
                new CardNumber("4000000000000002"), new Money(99_999, "USD"));
        assertThat(blockedAndOverLimit.reasonCode()).isEqualTo(ReasonCode.CARD_BLOCKED);
    }

    @Test
    void anAmountExactlyAtTheLimitIsApproved() {
        var result = engine.evaluate(
                new CardNumber("4242424242424242"), new Money(50_000, "USD"));

        assertThat(result.decision()).isEqualTo(com.ntccpay.auth.domain.model.Decision.APPROVED);
    }
}
