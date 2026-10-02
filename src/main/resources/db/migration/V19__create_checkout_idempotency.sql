CREATE TABLE don_hang_yeu_cau_luy_dang (
    id BINARY(16) NOT NULL,
    owner_subject VARCHAR(100) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    execution_id BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_order_id BINARY(16),
    failure_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_don_hang_luy_dang_scope
        UNIQUE (owner_subject, operation, idempotency_key_hash),
    CONSTRAINT uk_don_hang_luy_dang_execution UNIQUE (execution_id),
    CONSTRAINT uk_don_hang_luy_dang_order UNIQUE (result_order_id),
    CONSTRAINT fk_don_hang_luy_dang_order
        FOREIGN KEY (result_order_id) REFERENCES don_hang_don_dat_hang (id) ON DELETE RESTRICT,
    CONSTRAINT ck_don_hang_luy_dang_operation CHECK (operation = 'CREATE_ORDER'),
    CONSTRAINT ck_don_hang_luy_dang_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_don_hang_luy_dang_key_hash CHECK (CHAR_LENGTH(idempotency_key_hash) = 64),
    CONSTRAINT ck_don_hang_luy_dang_fingerprint CHECK (CHAR_LENGTH(request_fingerprint) = 64),
    CONSTRAINT ck_don_hang_luy_dang_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_don_hang_luy_dang_result CHECK (
        (status = 'PROCESSING' AND result_order_id IS NULL AND failure_code IS NULL)
        OR (status = 'COMPLETED' AND result_order_id IS NOT NULL AND failure_code IS NULL)
        OR (status = 'FAILED' AND result_order_id IS NULL AND failure_code IS NOT NULL)
    )
);

CREATE INDEX idx_don_hang_luy_dang_status_expiration
    ON don_hang_yeu_cau_luy_dang (status, expires_at);

CREATE INDEX idx_don_hang_luy_dang_owner_created
    ON don_hang_yeu_cau_luy_dang (owner_subject, created_at);
