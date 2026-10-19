CREATE TABLE thanh_toan_lan_thu (
    id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    attempt_number INT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(150),
    status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(100),
    completed_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_thanh_toan_lan_thu_order_attempt UNIQUE (order_id, attempt_number),
    CONSTRAINT uk_thanh_toan_lan_thu_provider_reference UNIQUE (provider_code, provider_reference),
    CONSTRAINT ck_thanh_toan_lan_thu_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_thanh_toan_lan_thu_amount CHECK (amount > 0),
    CONSTRAINT ck_thanh_toan_lan_thu_currency
        CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_thanh_toan_lan_thu_provider_code
        CHECK (CHAR_LENGTH(provider_code) BETWEEN 2 AND 50
            AND provider_code REGEXP '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_thanh_toan_lan_thu_provider_reference
        CHECK (provider_reference IS NULL OR CHAR_LENGTH(provider_reference) BETWEEN 1 AND 150),
    CONSTRAINT ck_thanh_toan_lan_thu_failure_code
        CHECK (failure_code IS NULL OR (
            CHAR_LENGTH(failure_code) BETWEEN 1 AND 100
            AND failure_code REGEXP '^[A-Z0-9][A-Z0-9_.-]*$'
        )),
    CONSTRAINT ck_thanh_toan_lan_thu_status CHECK (
        status IN ('CREATED', 'PENDING', 'REQUIRES_ACTION', 'UNKNOWN',
                   'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_thanh_toan_lan_thu_state CHECK (
        (status = 'CREATED'
            AND provider_reference IS NULL AND failure_code IS NULL AND completed_at IS NULL)
        OR (status IN ('PENDING', 'REQUIRES_ACTION')
            AND provider_reference IS NOT NULL AND failure_code IS NULL AND completed_at IS NULL)
        OR (status = 'UNKNOWN' AND failure_code IS NULL AND completed_at IS NULL)
        OR (status = 'SUCCEEDED'
            AND provider_reference IS NOT NULL AND failure_code IS NULL AND completed_at IS NOT NULL)
        OR (status = 'FAILED'
            AND failure_code IS NOT NULL AND completed_at IS NOT NULL)
        OR (status IN ('CANCELLED', 'EXPIRED')
            AND failure_code IS NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_thanh_toan_lan_thu_audit_time CHECK (
        updated_at >= created_at
        AND (completed_at IS NULL OR (completed_at >= created_at AND completed_at = updated_at))
    )
);

CREATE INDEX idx_thanh_toan_lan_thu_order_created
    ON thanh_toan_lan_thu (order_id, created_at);

CREATE INDEX idx_thanh_toan_lan_thu_status_updated
    ON thanh_toan_lan_thu (status, updated_at);
