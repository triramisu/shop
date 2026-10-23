CREATE TABLE thanh_toan_nhip_doi_soat (
    provider_code VARCHAR(50) NOT NULL PRIMARY KEY,
    next_claim_at TIMESTAMP(6) NOT NULL
);
INSERT INTO thanh_toan_nhip_doi_soat (provider_code, next_claim_at)
    VALUES ('VNPAY', '2000-01-01 00:00:00');

CREATE TABLE thanh_toan_doi_soat (
    payment_attempt_id BINARY(16) NOT NULL PRIMARY KEY,
    state VARCHAR(20) NOT NULL,
    query_count INT NOT NULL DEFAULT 0,
    next_query_at TIMESTAMP(6) NOT NULL,
    deadline_at TIMESTAMP(6) NOT NULL,
    lease_token BINARY(16),
    lease_started_at TIMESTAMP(6),
    lease_until TIMESTAMP(6),
    payment_version BIGINT,
    last_error VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_doi_soat_payment FOREIGN KEY (payment_attempt_id) REFERENCES thanh_toan_lan_thu(id),
    CONSTRAINT ck_doi_soat_count CHECK (query_count BETWEEN 0 AND 20),
    CONSTRAINT ck_doi_soat_version CHECK (payment_version IS NULL OR payment_version >= 0),
    CONSTRAINT ck_doi_soat_state CHECK (state IN ('READY', 'LEASED', 'DONE', 'MANUAL')),
    CONSTRAINT ck_doi_soat_lease CHECK (
        (state = 'LEASED' AND lease_token IS NOT NULL AND lease_started_at IS NOT NULL
            AND lease_until IS NOT NULL AND lease_until > lease_started_at AND payment_version IS NOT NULL)
        OR (state <> 'LEASED' AND lease_token IS NULL AND lease_started_at IS NULL
            AND lease_until IS NULL AND payment_version IS NULL)),
    CONSTRAINT ck_doi_soat_time CHECK (updated_at >= created_at)
);
CREATE INDEX idx_doi_soat_due ON thanh_toan_doi_soat(state, next_query_at, lease_until);
CREATE INDEX idx_payment_reconcile_discovery ON thanh_toan_lan_thu(provider_code, status, created_at, id);

CREATE TABLE thanh_toan_lich_su_doi_soat (
    id BINARY(16) NOT NULL PRIMARY KEY,
    payment_attempt_id BINARY(16) NOT NULL,
    lease_token BINARY(16) NOT NULL,
    query_count INT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    provider_code VARCHAR(50) NOT NULL,
    provider_response_id VARCHAR(32),
    provider_transaction_no VARCHAR(100),
    payload_hash CHAR(64),
    receipt_hash CHAR(64),
    event_id BINARY(16),
    query_started_at TIMESTAMP(6) NOT NULL,
    observed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_lich_su_doi_soat_payment FOREIGN KEY (payment_attempt_id) REFERENCES thanh_toan_lan_thu(id),
    CONSTRAINT uk_doi_soat_lease UNIQUE (lease_token),
    CONSTRAINT uk_doi_soat_receipt UNIQUE (provider_code, receipt_hash),
    CONSTRAINT ck_lich_su_doi_soat_count CHECK (query_count BETWEEN 0 AND 20),
    CONSTRAINT ck_doi_soat_receipt CHECK (receipt_hash IS NULL OR (payload_hash IS NOT NULL AND receipt_hash = payload_hash)),
    CONSTRAINT ck_doi_soat_hash CHECK (payload_hash IS NULL OR payload_hash REGEXP '^[a-f0-9]{64}$')
);
CREATE INDEX idx_lich_su_doi_soat_payment ON thanh_toan_lich_su_doi_soat(payment_attempt_id, created_at);
