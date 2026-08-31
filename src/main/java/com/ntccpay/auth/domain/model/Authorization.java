package com.ntccpay.auth.domain.model;

import com.ntccpay.auth.domain.event.AuthorizationApproved;
import com.ntccpay.auth.domain.event.AuthorizationDeclined;
import com.ntccpay.auth.domain.event.AuthorizationRequested;
import com.ntccpay.auth.domain.event.DomainEvent;
import com.ntccpay.auth.domain.service.DecisionResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Aggregate root of the Auth Context.
 *
 * Invariants:
 * <ul>
 *   <li>a decision, once made, is immutable</li>
 *   <li>one decision per idempotency key (enforced with the repository's unique key)</li>
 * </ul>
 */
public final class Authorization {

    private final AuthorizationId id;
    private final IdempotencyKey idempotencyKey;
    private final String requestFingerprint;
    private final CardNumber cardNumber;
    private final Money amount;
    private final MerchantId merchant;

    private Decision decision;
    private ReasonCode reasonCode;
    private Instant decidedAt;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Authorization(AuthorizationId id,
                          IdempotencyKey idempotencyKey,
                          String requestFingerprint,
                          CardNumber cardNumber,
                          Money amount,
                          MerchantId merchant) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.cardNumber = cardNumber;
        this.amount = amount;
        this.merchant = merchant;
    }

    /** Factory: a new authorization has been requested. */
    public static Authorization request(AuthorizationId id,
                                        IdempotencyKey idempotencyKey,
                                        String requestFingerprint,
                                        CardNumber cardNumber,
                                        Money amount,
                                        MerchantId merchant) {
        var authorization = new Authorization(id, idempotencyKey, requestFingerprint, cardNumber, amount, merchant);
        authorization.domainEvents.add(new AuthorizationRequested(id, Instant.now()));
        return authorization;
    }

    /** Applies the rule engine's outcome. Idempotent decisions are forbidden: decide once. */
    public void decide(DecisionResult result) {
        if (decision != null) {
            throw new IllegalStateException("a decision, once made, is immutable");
        }
        this.decision = result.decision();
        this.reasonCode = result.reasonCode();
        this.decidedAt = Instant.now();
        if (decision == Decision.APPROVED) {
            domainEvents.add(new AuthorizationApproved(id, decidedAt));
        } else {
            domainEvents.add(new AuthorizationDeclined(id, reasonCode, decidedAt));
        }
    }

    /**
     * Persistence rehydration: rebuilds a stored, already-decided aggregate.
     * For the infrastructure persistence adapter only — raises no domain events
     * (events are raised when a request arrives or a decision is made, not when
     * history is loaded). The aggregate stays persistence-ignorant.
     */
    public static Authorization rehydrate(AuthorizationId id,
                                          IdempotencyKey idempotencyKey,
                                          String requestFingerprint,
                                          CardNumber cardNumber,
                                          Money amount,
                                          MerchantId merchant,
                                          Decision decision,
                                          ReasonCode reasonCode,
                                          Instant decidedAt) {
        if (decision == null) {
            throw new IllegalArgumentException("a stored authorization must carry a decision");
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("a stored authorization must carry decidedAt");
        }
        var authorization = new Authorization(id, idempotencyKey, requestFingerprint, cardNumber, amount, merchant);
        authorization.decision = decision;
        authorization.reasonCode = reasonCode;
        authorization.decidedAt = decidedAt;
        return authorization;
    }

    /**
     * Stable fingerprint of the request contents. Same key + same fingerprint means
     * "a retry of the same request"; same key + different fingerprint means conflict.
     */
    public static String fingerprintOf(CardNumber card, Money amount, MerchantId merchant) {
        var payload = card.raw() + "|" + amount.minorUnits() + "|" + amount.currencyCode() + "|" + merchant.value();
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public AuthorizationId id() {
        return id;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public CardNumber cardNumber() {
        return cardNumber;
    }

    public Money amount() {
        return amount;
    }

    public MerchantId merchant() {
        return merchant;
    }

    public Decision decision() {
        return decision;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public List<DomainEvent> domainEvents() {
        return List.copyOf(domainEvents);
    }
}
