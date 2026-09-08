package com.ntccpay.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/** Business rule configuration: currencies, limits, blocklists. */
@ConfigurationProperties(prefix = "ntccpay.rules")
public record RuleEngineProperties(Set<String> supportedCurrencies,
                                   long perTransactionLimit,
                                   Set<String> blockedCards,
                                   Set<String> blockedBins) {

    public RuleEngineProperties {
        supportedCurrencies = supportedCurrencies == null ? Set.of() : Set.copyOf(supportedCurrencies);
        blockedCards = blockedCards == null ? Set.of() : Set.copyOf(blockedCards);
        blockedBins = blockedBins == null ? Set.of() : Set.copyOf(blockedBins);
    }
}
