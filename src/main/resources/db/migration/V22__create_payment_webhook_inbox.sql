CREATE TABLE thanh_toan_su_kien_webhook (
    id BINARY(16) NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(150) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    provider_object_id VARCHAR(150) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    signature_timestamp TIMESTAMP(6) NOT NULL,
    provider_created_at TIMESTAMP(6) NOT NULL,
    payment_attempt_id BINARY(16),
    outcome VARCHAR(30) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_thanh_toan_webhook_provider_event UNIQUE (provider_code, provider_event_id),
    CONSTRAINT fk_thanh_toan_webhook_attempt FOREIGN KEY (payment_attempt_id)
        REFERENCES thanh_toan_lan_thu (id),
    CONSTRAINT ck_thanh_toan_webhook_provider_code CHECK (
        CHAR_LENGTH(provider_code) BETWEEN 2 AND 50
        AND provider_code REGEXP '^[A-Z0-9][A-Z0-9_-]*$'
    ),
    CONSTRAINT ck_thanh_toan_webhook_event_id CHECK (
        CHAR_LENGTH(provider_event_id) BETWEEN 1 AND 150
    ),
    CONSTRAINT ck_thanh_toan_webhook_event_type CHECK (
        CHAR_LENGTH(event_type) BETWEEN 1 AND 100
    ),
    CONSTRAINT ck_thanh_toan_webhook_object_id CHECK (
        CHAR_LENGTH(provider_object_id) BETWEEN 1 AND 150
    ),
    CONSTRAINT ck_thanh_toan_webhook_payload_hash CHECK (
        payload_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_thanh_toan_webhook_outcome CHECK (
        outcome IN ('RECEIVED', 'APPLIED', 'ALREADY_APPLIED', 'IGNORED_UNSUPPORTED', 'IGNORED_TERMINAL')
    ),
    CONSTRAINT ck_thanh_toan_webhook_attempt_outcome CHECK (
        (outcome = 'RECEIVED')
        OR (outcome = 'IGNORED_UNSUPPORTED' AND payment_attempt_id IS NULL)
        OR (outcome IN ('APPLIED', 'ALREADY_APPLIED', 'IGNORED_TERMINAL')
            AND payment_attempt_id IS NOT NULL)
    ),
    CONSTRAINT ck_thanh_toan_webhook_time CHECK (
        processed_at >= received_at
    )
);

CREATE INDEX idx_thanh_toan_webhook_attempt_received
    ON thanh_toan_su_kien_webhook (payment_attempt_id, received_at);

CREATE INDEX idx_thanh_toan_webhook_outcome_received
    ON thanh_toan_su_kien_webhook (outcome, received_at);
