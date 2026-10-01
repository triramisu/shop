CREATE TABLE don_hang_dieu_phoi_ton_kho (
    id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    request_event_id BINARY(16) NOT NULL,
    correlation_id BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    failure_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_don_hang_dieu_phoi_ton_kho_order UNIQUE (order_id),
    CONSTRAINT uk_don_hang_dieu_phoi_ton_kho_event UNIQUE (request_event_id),
    CONSTRAINT uk_don_hang_dieu_phoi_ton_kho_correlation UNIQUE (correlation_id),
    CONSTRAINT fk_don_hang_dieu_phoi_ton_kho_order
        FOREIGN KEY (order_id) REFERENCES don_hang_don_dat_hang (id) ON DELETE CASCADE,
    CONSTRAINT ck_don_hang_dieu_phoi_ton_kho_status CHECK (
        status IN (
            'REQUESTED', 'PROCESSING', 'RESERVED', 'RETRY_REQUIRED',
            'COMPENSATING', 'FAILED', 'COMPENSATION_REQUIRED'
        )
    ),
    CONSTRAINT ck_don_hang_dieu_phoi_ton_kho_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_don_hang_dieu_phoi_ton_kho_status_updated
    ON don_hang_dieu_phoi_ton_kho (status, updated_at);

CREATE TABLE don_hang_dong_giu_ton_kho (
    reservation_id BINARY(16) NOT NULL,
    orchestration_id BINARY(16) NOT NULL,
    order_item_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    line_number INT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity BIGINT NOT NULL,
    stock_item_id BINARY(16),
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (reservation_id),
    CONSTRAINT uk_don_hang_dong_giu_ton_kho_item UNIQUE (order_item_id),
    CONSTRAINT uk_don_hang_dong_giu_ton_kho_line UNIQUE (orchestration_id, line_number),
    CONSTRAINT fk_don_hang_dong_giu_ton_kho_orchestration
        FOREIGN KEY (orchestration_id) REFERENCES don_hang_dieu_phoi_ton_kho (id) ON DELETE CASCADE,
    CONSTRAINT fk_don_hang_dong_giu_ton_kho_item
        FOREIGN KEY (order_item_id) REFERENCES don_hang_muc_don_hang (id) ON DELETE CASCADE,
    CONSTRAINT ck_don_hang_dong_giu_ton_kho_quantity CHECK (quantity > 0),
    CONSTRAINT ck_don_hang_dong_giu_ton_kho_line CHECK (line_number > 0),
    CONSTRAINT ck_don_hang_dong_giu_ton_kho_status
        CHECK (status IN ('PENDING', 'RESERVED', 'RELEASED', 'FAILED'))
);

CREATE INDEX idx_don_hang_dong_giu_ton_kho_orchestration
    ON don_hang_dong_giu_ton_kho (orchestration_id);

CREATE INDEX idx_don_hang_dong_giu_ton_kho_stock_item
    ON don_hang_dong_giu_ton_kho (stock_item_id);
