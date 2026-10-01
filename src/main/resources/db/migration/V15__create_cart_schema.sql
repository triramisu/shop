CREATE TABLE don_hang_gio_hang (
    id BINARY(16) NOT NULL,
    owner_subject VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_don_hang_gio_hang_owner UNIQUE (owner_subject)
);

CREATE TABLE don_hang_muc_gio_hang (
    id BINARY(16) NOT NULL,
    cart_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_don_hang_muc_gio_hang_cart
        FOREIGN KEY (cart_id) REFERENCES don_hang_gio_hang (id) ON DELETE CASCADE,
    CONSTRAINT uk_don_hang_muc_gio_hang_variant UNIQUE (cart_id, product_variant_id),
    CONSTRAINT uk_don_hang_muc_gio_hang_sku UNIQUE (cart_id, sku),
    CONSTRAINT ck_don_hang_muc_gio_hang_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE INDEX idx_don_hang_muc_gio_hang_variant ON don_hang_muc_gio_hang (product_variant_id);
