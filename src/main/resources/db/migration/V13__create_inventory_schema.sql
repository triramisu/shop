CREATE TABLE ton_kho_mat_hang (
    id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    location_code VARCHAR(64) NOT NULL,
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ton_kho_mat_hang_variant_location UNIQUE (product_variant_id, location_code),
    CONSTRAINT uk_ton_kho_mat_hang_sku_location UNIQUE (sku, location_code),
    CONSTRAINT ck_ton_kho_mat_hang_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_ton_kho_mat_hang_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_ton_kho_mat_hang_balance CHECK (reserved_quantity <= on_hand)
);

CREATE TABLE ton_kho_bien_dong (
    id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    on_hand_delta BIGINT NOT NULL,
    reserved_delta BIGINT NOT NULL,
    on_hand_after BIGINT NOT NULL,
    reserved_after BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reference_id VARCHAR(100),
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ton_kho_bien_dong_mat_hang
        FOREIGN KEY (stock_item_id) REFERENCES ton_kho_mat_hang (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ton_kho_bien_dong_type
        CHECK (movement_type IN ('INITIAL', 'ADJUSTMENT', 'RESERVATION', 'CONFIRMATION', 'RELEASE', 'EXPIRATION')),
    CONSTRAINT ck_ton_kho_bien_dong_delta CHECK (on_hand_delta <> 0 OR reserved_delta <> 0),
    CONSTRAINT ck_ton_kho_bien_dong_on_hand CHECK (on_hand_after >= 0),
    CONSTRAINT ck_ton_kho_bien_dong_reserved CHECK (reserved_after >= 0),
    CONSTRAINT ck_ton_kho_bien_dong_balance CHECK (reserved_after <= on_hand_after)
);

CREATE TABLE ton_kho_giu_hang (
    id BINARY(16) NOT NULL,
    stock_item_id BINARY(16) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ton_kho_giu_hang_mat_hang
        FOREIGN KEY (stock_item_id) REFERENCES ton_kho_mat_hang (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ton_kho_giu_hang_quantity CHECK (quantity > 0),
    CONSTRAINT ck_ton_kho_giu_hang_status
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_ton_kho_giu_hang_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_ton_kho_mat_hang_location ON ton_kho_mat_hang (location_code, sku);
CREATE INDEX idx_ton_kho_bien_dong_item_time ON ton_kho_bien_dong (stock_item_id, occurred_at);
CREATE INDEX idx_ton_kho_bien_dong_reference ON ton_kho_bien_dong (reference_id);
CREATE INDEX idx_ton_kho_giu_hang_item_status ON ton_kho_giu_hang (stock_item_id, status);
CREATE INDEX idx_ton_kho_giu_hang_status_expiration ON ton_kho_giu_hang (status, expires_at);

INSERT INTO xac_thuc_quyen_han (code, description)
VALUES ('INVENTORY_READ', 'Xem số dư và lịch sử biến động tồn kho'),
       ('INVENTORY_WRITE', 'Khởi tạo và điều chỉnh tồn kho');

INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
VALUES ('ADMIN', 'INVENTORY_READ'),
       ('ADMIN', 'INVENTORY_WRITE'),
       ('STAFF', 'INVENTORY_READ'),
       ('STAFF', 'INVENTORY_WRITE');

UPDATE xac_thuc_phien_lam_moi
SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL;
