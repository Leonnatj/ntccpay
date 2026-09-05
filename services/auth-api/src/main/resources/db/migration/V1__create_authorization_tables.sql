-- Phase 2: auth persistence. Flyway owns this schema; Hibernate validates only.
-- Money correctness: amounts are BIGINT minor units + CHAR(3) ISO 4217, never floats.
-- PCI: the full PAN is never stored — only the masked form (****1234). The
-- request_fingerprint (SHA-256 over PAN|amount|currency|merchant) is what
-- idempotent replay checks, never the raw card data.

CREATE TABLE authorizations (
    id                  UUID           PRIMARY KEY,
    card_masked         VARCHAR(16)    NOT NULL,
    amount_minor        BIGINT         NOT NULL CHECK (amount_minor >= 0),
    currency            CHAR(3)        NOT NULL,
    merchant_id         VARCHAR(120)   NOT NULL,
    decision            VARCHAR(16)    NOT NULL CHECK (decision IN ('APPROVED', 'DECLINED')),
    reason_code         VARCHAR(64),
    decided_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_authorizations_merchant ON authorizations (merchant_id);
CREATE INDEX idx_authorizations_decided_at ON authorizations (decided_at);

-- One decision per idempotency key: the PRIMARY KEY is the guarantee.
-- Concurrent duplicate requests cannot double-insert (Phase 2 exit criterion).
CREATE TABLE idempotency_keys (
    idempotency_key     VARCHAR(100)   PRIMARY KEY,
    request_fingerprint CHAR(64)       NOT NULL,
    authorization_id    UUID           NOT NULL UNIQUE REFERENCES authorizations (id),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
