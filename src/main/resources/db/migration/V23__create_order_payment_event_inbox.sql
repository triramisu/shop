ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD COLUMN payment_attempt_id BINARY(16);

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD COLUMN payment_attempt_number INT;

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD COLUMN last_payment_status VARCHAR(30);

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD COLUMN last_payment_event_at TIMESTAMP(6);

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD CONSTRAINT uk_don_hang_dieu_phoi_ton_kho_payment_attempt UNIQUE (payment_attempt_id);

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD CONSTRAINT ck_don_hang_dieu_phoi_ton_kho_payment_attempt CHECK (
        (payment_attempt_id IS NULL AND payment_attempt_number IS NULL)
        OR (payment_attempt_id IS NOT NULL AND payment_attempt_number > 0)
    );

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD CONSTRAINT ck_don_hang_dieu_phoi_ton_kho_payment_event CHECK (
        (last_payment_status IS NULL AND last_payment_event_at IS NULL)
        OR (last_payment_status IS NOT NULL AND last_payment_event_at IS NOT NULL)
    );

CREATE TABLE don_hang_su_kien_thanh_toan (
    id BINARY(16) NOT NULL,
    event_id BINARY(16) NOT NULL,
    event_version INT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    payment_attempt_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    previous_payment_status VARCHAR(30) NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(150),
    payment_failure_code VARCHAR(100),
    outcome VARCHAR(30) NOT NULL,
    processing_failure_code VARCHAR(100),
    event_occurred_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_don_hang_su_kien_thanh_toan_event UNIQUE (event_id),
    CONSTRAINT ck_don_hang_su_kien_thanh_toan_version CHECK (event_version = 1),
    CONSTRAINT ck_don_hang_su_kien_thanh_toan_hash CHECK (payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_don_hang_su_kien_thanh_toan_amount CHECK (amount > 0),
    CONSTRAINT ck_don_hang_su_kien_thanh_toan_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_don_hang_su_kien_thanh_toan_outcome CHECK (
        outcome IN ('RECEIVED', 'PROCESSING', 'COMPLETED', 'IGNORED',
                    'RETRY_REQUIRED', 'MANUAL_ACTION_REQUIRED')
    ),
    CONSTRAINT ck_don_hang_su_kien_thanh_toan_processed CHECK (
        (outcome IN ('RECEIVED', 'PROCESSING') AND processed_at IS NULL)
        OR (outcome IN ('COMPLETED', 'IGNORED', 'RETRY_REQUIRED', 'MANUAL_ACTION_REQUIRED')
            AND processed_at IS NOT NULL)
    )
);

CREATE INDEX idx_don_hang_su_kien_thanh_toan_outcome_received
    ON don_hang_su_kien_thanh_toan (outcome, received_at);

CREATE INDEX idx_don_hang_su_kien_thanh_toan_order_occurred
    ON don_hang_su_kien_thanh_toan (order_id, event_occurred_at);
