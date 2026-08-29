package com.ntccpay.auth.domain.service;

import com.ntccpay.auth.domain.model.Bin;
import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.model.ReasonCode;

import java.util.Set;

/**
 * Pure domain service: the business rules, evaluated in order.
 * The first failing rule determines the Reason Code. No Spring, no I/O.
 */
public final class AuthorizationRuleEngine {

    private final Set<String> supportedCurrencies;
    private final long perTransactionLimit;
    private final Set<String> blockedCards;
    private final Set<String> blockedBins;

    public AuthorizationRuleEngine(Set<String> supportedCurrencies,
                                   long perTransactionLimit,
                                   Set<String> blockedCards,
                                   Set<String> blockedBins) {
        this.supportedCurrencies = Set.copyOf(supportedCurrencies);
        this.perTransactionLimit = perTransactionLimit;
        this.blockedCards = Set.copyOf(blockedCards);
        this.blockedBins = Set.copyOf(blockedBins);
    }

    public DecisionResult evaluate(CardNumber card, Money amount) {
        if (!card.luhnValid()) {
            return DecisionResult.declined(ReasonCode.INVALID_PAN);
        }
        if (blockedCards.contains(card.raw()) || blockedBins.contains(card.bin().value())) {
            return DecisionResult.declined(ReasonCode.CARD_BLOCKED);
        }
        if (!supportedCurrencies.contains(amount.currencyCode())) {
            return DecisionResult.declined(ReasonCode.CURRENCY_NOT_SUPPORTED);
        }
        if (amount.minorUnits() > perTransactionLimit) {
            return DecisionResult.declined(ReasonCode.AMOUNT_EXCEEDS_LIMIT);
        }
        return DecisionResult.approved();
    }
}
