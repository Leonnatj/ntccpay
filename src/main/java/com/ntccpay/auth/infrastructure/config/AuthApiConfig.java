package com.ntccpay.auth.infrastructure.config;

import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.domain.service.AuthorizationRuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires domain objects from configuration - the composition root. */
@Configuration
public class AuthApiConfig {

    @Bean
    public AuthorizationRuleEngine authorizationRuleEngine(RuleEngineProperties properties) {
        return new AuthorizationRuleEngine(
                properties.supportedCurrencies(),
                properties.perTransactionLimit(),
                properties.blockedCards(),
                properties.blockedBins());
    }

    @Bean
    public AuthorizationRepository authorizationRepository() {
        return new com.ntccpay.auth.infrastructure.persistence.InMemoryAuthorizationRepository();
    }
}
