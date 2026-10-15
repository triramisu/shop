CREATE TABLE ton_kho_yeu_cau_luy_dang (
    id BINARY(16) NOT NULL,
    reservation_id BINARY(16) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    result_stock_item_id BINARY(16) NOT NULL,
    result_quantity BIGINT NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    result_expires_at TIMESTAMP(6) NOT NULL,
    result_on_hand BIGINT NOT NULL,
    result_reserved BIGINT NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ton_kho_luy_dang_reservation_operation UNIQUE (reservation_id, operation),
    CONSTRAINT fk_ton_kho_luy_dang_reservation
        FOREIGN KEY (reservation_id) REFERENCES ton_kho_giu_hang (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ton_kho_luy_dang_mat_hang
        FOREIGN KEY (result_stock_item_id) REFERENCES ton_kho_mat_hang (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ton_kho_luy_dang_operation CHECK (operation IN ('RESERVE', 'CONFIRM', 'RELEASE')),
    CONSTRAINT ck_ton_kho_luy_dang_processing CHECK (processing_status = 'COMPLETED'),
    CONSTRAINT ck_ton_kho_luy_dang_fingerprint CHECK (CHAR_LENGTH(request_fingerprint) = 64),
    CONSTRAINT ck_ton_kho_luy_dang_quantity CHECK (result_quantity > 0),
    CONSTRAINT ck_ton_kho_luy_dang_status
        CHECK (result_status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_ton_kho_luy_dang_on_hand CHECK (result_on_hand >= 0),
    CONSTRAINT ck_ton_kho_luy_dang_reserved CHECK (result_reserved >= 0),
    CONSTRAINT ck_ton_kho_luy_dang_balance CHECK (result_reserved <= result_on_hand)
);

CREATE INDEX idx_ton_kho_luy_dang_completed ON ton_kho_yeu_cau_luy_dang (completed_at);
