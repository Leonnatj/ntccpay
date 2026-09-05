package com.ntccpay.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;

/**
 * Persistence model of the idempotency guarantee. The PRIMARY KEY on
 * {@code idempotency_key} is what makes concurrent duplicate requests
 * unable to double-insert — the database enforces the invariant, not
 * application code.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorization_id", nullable = false, unique = true)
    private AuthorizationEntity authorization;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
        // for JPA
    }

    private IdempotencyKeyEntity(String idempotencyKey, String requestFingerprint,
                                 AuthorizationEntity authorization, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.authorization = authorization;
        this.createdAt = createdAt;
    }

    public static IdempotencyKeyEntity of(String idempotencyKey, String requestFingerprint,
                                          AuthorizationEntity authorization) {
        return new IdempotencyKeyEntity(idempotencyKey, requestFingerprint, authorization, Instant.now());
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public AuthorizationEntity getAuthorization() {
        return authorization;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
