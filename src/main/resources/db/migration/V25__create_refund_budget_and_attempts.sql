CREATE TABLE thanh_toan_ngan_sach_hoan (
    payment_attempt_id BINARY(16) NOT NULL,
    captured_amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    allocated_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    succeeded_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (payment_attempt_id),
    CONSTRAINT fk_thanh_toan_ngan_sach_payment FOREIGN KEY (payment_attempt_id)
        REFERENCES thanh_toan_lan_thu (id),
    CONSTRAINT ck_thanh_toan_ngan_sach_amount CHECK (
        captured_amount > 0 AND succeeded_amount >= 0
        AND allocated_amount >= succeeded_amount AND allocated_amount <= captured_amount),
    CONSTRAINT ck_thanh_toan_ngan_sach_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_thanh_toan_ngan_sach_time CHECK (updated_at >= created_at)
);

CREATE TABLE thanh_toan_lan_hoan (
    id BINARY(16) NOT NULL,
    payment_attempt_id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    provider_code VARCHAR(50) NOT NULL,
    provider_payment_reference VARCHAR(150) NOT NULL,
    provider_reference VARCHAR(150),
    key_hash CHAR(64) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    actor_subject VARCHAR(100) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_thanh_toan_lan_hoan_budget FOREIGN KEY (payment_attempt_id)
        REFERENCES thanh_toan_ngan_sach_hoan (payment_attempt_id),
    CONSTRAINT uk_thanh_toan_lan_hoan_key UNIQUE (payment_attempt_id, key_hash),
    CONSTRAINT uk_thanh_toan_lan_hoan_provider UNIQUE (provider_code, provider_reference),
    CONSTRAINT ck_thanh_toan_lan_hoan_amount CHECK (amount > 0),
    CONSTRAINT ck_thanh_toan_lan_hoan_currency CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_thanh_toan_lan_hoan_key CHECK (key_hash REGEXP '^[a-f0-9]{64}$'),
    CONSTRAINT ck_thanh_toan_lan_hoan_fingerprint CHECK (fingerprint REGEXP '^[a-f0-9]{64}$'),
    CONSTRAINT ck_thanh_toan_lan_hoan_actor CHECK (CHAR_LENGTH(actor_subject) BETWEEN 1 AND 100),
    CONSTRAINT ck_thanh_toan_lan_hoan_provider CHECK (
        CHAR_LENGTH(provider_code) BETWEEN 2 AND 50
        AND provider_code REGEXP '^[A-Z0-9][A-Z0-9_-]*$'
        AND CHAR_LENGTH(provider_payment_reference) BETWEEN 1 AND 150
        AND (provider_reference IS NULL OR CHAR_LENGTH(provider_reference) BETWEEN 1 AND 150)),
    CONSTRAINT ck_thanh_toan_lan_hoan_failure CHECK (
        failure_code IS NULL OR failure_code REGEXP '^[A-Z0-9][A-Z0-9_.-]*$'),
    CONSTRAINT ck_thanh_toan_lan_hoan_state CHECK (
        (status = 'CREATED' AND provider_reference IS NULL AND failure_code IS NULL)
        OR (status = 'UNKNOWN' AND failure_code IS NULL)
        OR (status IN ('PENDING', 'SUCCEEDED') AND provider_reference IS NOT NULL AND failure_code IS NULL)
        OR (status = 'FAILED' AND failure_code IS NOT NULL)),
    CONSTRAINT ck_thanh_toan_lan_hoan_time CHECK (updated_at >= created_at)
);

CREATE INDEX idx_thanh_toan_lan_hoan_status_updated ON thanh_toan_lan_hoan (status, updated_at);

INSERT INTO xac_thuc_quyen_han (code, description)
VALUES ('PAYMENT_REFUND', 'Yêu cầu và tra cứu hoàn tiền có kiểm soát');
INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
VALUES ('ADMIN', 'PAYMENT_REFUND');

-- Token có claim cũ phải đăng nhập lại sau thay đổi quyền.
UPDATE xac_thuc_phien_lam_moi SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL AND user_id IN (
    SELECT user_id FROM xac_thuc_nguoi_dung_vai_tro WHERE role_code = 'ADMIN'
);
