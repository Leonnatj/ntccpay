package com.ntccpay.auth.infrastructure.config;

import com.ntccpay.auth.application.port.in.RequestAuthorization;
import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.application.usecase.AuthorizationRequestService;
import com.ntccpay.auth.domain.service.AuthorizationRuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires domain and application objects from configuration - the composition root. */
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
    public RequestAuthorization authorizationRequestService(
            AuthorizationRepository authorizationRepository,
            AuthorizationRuleEngine authorizationRuleEngine) {
        return new AuthorizationRequestService(authorizationRepository, authorizationRuleEngine);
    }
}
