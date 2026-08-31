package com.ntccpay.auth.application.usecase;

import com.ntccpay.auth.application.exception.IdempotencyConflictException;
import com.ntccpay.auth.application.port.in.AuthorizationCommand;
import com.ntccpay.auth.application.port.in.AuthorizationResult;
import com.ntccpay.auth.application.port.in.RequestAuthorization;
import com.ntccpay.auth.application.exception.IdempotencyRaceException;
import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.AuthorizationId;
import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.IdempotencyKey;
import com.ntccpay.auth.domain.model.MerchantId;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.service.AuthorizationRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates: idempotency check → rule evaluation → aggregate decision → save.
 * This is application logic only; the rules live in the domain.
 */
@Service
public class AuthorizationRequestService implements RequestAuthorization {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationRequestService.class);

    private final AuthorizationRepository repository;
    private final AuthorizationRuleEngine ruleEngine;

    public AuthorizationRequestService(AuthorizationRepository repository, AuthorizationRuleEngine ruleEngine) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
    }

    @Override
    public AuthorizationResult handle(AuthorizationCommand command) {
        var key = new IdempotencyKey(command.idempotencyKey());
        var card = new CardNumber(command.pan());
        var amount = new Money(command.amountMinor(), command.currency());
        var merchant = new MerchantId(command.merchant());
        var fingerprint = Authorization.fingerprintOf(card, amount, merchant);

        Optional<Authorization> existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), key, fingerprint);
        }

        var decision = ruleEngine.evaluate(card, amount);
        var authorization = Authorization.request(
                AuthorizationId.newId(), key, fingerprint, card, amount, merchant);
        authorization.decide(decision);
        try {
            repository.save(authorization);
        } catch (IdempotencyRaceException race) {
            // Our lookup found nothing, but another request with the same key committed
            // before our insert landed (the DB PRIMARY KEY is the arbiter). Re-read the
            // winner and apply the same rules as the pre-check path: replay for an
            // identical request, conflict for a different one.
            var winner = repository.findByIdempotencyKey(key).orElseThrow(() -> race);
            return replayOrConflict(winner, key, fingerprint);
        }

        // cardNumber.toString() is masked by construction - safe to log.
        log.info("Authorization decided: id={}, card={}, decision={}, reason={}",
                authorization.id(), authorization.cardNumber(), authorization.decision(), authorization.reasonCode());
        return AuthorizationResult.of(authorization, false);
    }

    /** Same key + same fingerprint = a retry of the same request (replay); different contents = conflict. */
    private AuthorizationResult replayOrConflict(Authorization original, IdempotencyKey key, String fingerprint) {
        if (!original.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException(key);
        }
        log.info("Idempotent replay for key {}: returning original decision", key.value());
        return AuthorizationResult.replayOf(original);
    }
}
