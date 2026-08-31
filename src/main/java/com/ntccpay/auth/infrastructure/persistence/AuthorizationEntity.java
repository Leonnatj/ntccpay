package com.ntccpay.auth.infrastructure.persistence;

import com.ntccpay.auth.application.port.out.AuthorizationRepository;
import com.ntccpay.auth.domain.model.Authorization;
import com.ntccpay.auth.domain.model.AuthorizationId;
import com.ntccpay.auth.domain.model.CardNumber;
import com.ntccpay.auth.domain.model.Decision;
import com.ntccpay.auth.domain.model.IdempotencyKey;
import com.ntccpay.auth.domain.model.MerchantId;
import com.ntccpay.auth.domain.model.Money;
import com.ntccpay.auth.domain.model.ReasonCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.domain.Persistable;

import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model of the Authorization aggregate. Lives only in the
 * infrastructure adapter: the domain aggregate carries no JPA annotations
 * (persistence model &ne; domain model). The full PAN is never stored —
 * only the masked form. PCI by construction.
 *
 * Implements {@link Persistable} so Spring Data {@code persist()}s new
 * aggregates with their assigned UUID instead of merge/select-probing.
 */
@Entity
@Table(name = "authorizations")
public class AuthorizationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "card_masked", nullable = false, length = 16)
    private String cardMasked;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "merchant_id", nullable = false, length = 120)
    private String merchantId;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    /** Optimistic locking; the write path is append-only so this stays at 0. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "authorization", fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST, optional = false)
    private IdempotencyKeyEntity idempotencyKey;

    @Transient
    private boolean isNew = true;

    protected AuthorizationEntity() {
        // for JPA
    }

    private AuthorizationEntity(UUID id) {
        this.id = id;
        this.createdAt = Instant.now();
    }

    public static AuthorizationEntity newAuthorization(UUID id) {
        return new AuthorizationEntity(id);
    }

    public UUID getId() {
        return id;
    }

    public String getCardMasked() {
        return cardMasked;
    }

    public void setCardMasked(String cardMasked) {
        this.cardMasked = cardMasked;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public IdempotencyKeyEntity getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(IdempotencyKeyEntity idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    // ---- mapping: the only place the persistence model and the domain model meet ----

    public static AuthorizationEntity fromDomain(Authorization authorization) {
        var entity = newAuthorization(authorization.id().value());
        entity.cardMasked = authorization.cardNumber().masked();
        entity.amountMinor = authorization.amount().minorUnits();
        entity.currency = authorization.amount().currencyCode();
        entity.merchantId = authorization.merchant().value();
        entity.decision = authorization.decision().name();
        entity.reasonCode = authorization.reasonCode() == null ? null : authorization.reasonCode().name();
        entity.decidedAt = authorization.decidedAt();
        entity.idempotencyKey = IdempotencyKeyEntity.of(
                authorization.idempotencyKey().value(),
                authorization.requestFingerprint(),
                entity);
        return entity;
    }

    /** Rehydrates the domain aggregate. Only the masked card survives the roundtrip. */
    public Authorization toDomain() {
        return Authorization.rehydrate(
                new AuthorizationId(id),
                new IdempotencyKey(idempotencyKey.getIdempotencyKey()),
                idempotencyKey.getRequestFingerprint(),
                CardNumber.maskedReference(cardMasked),
                new Money(amountMinor, currency),
                new MerchantId(merchantId),
                Decision.valueOf(decision),
                reasonCode == null ? null : ReasonCode.valueOf(reasonCode),
                decidedAt);
    }
}
