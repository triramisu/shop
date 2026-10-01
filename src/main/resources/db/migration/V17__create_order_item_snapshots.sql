CREATE TABLE don_hang_muc_don_hang (
    id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    line_number INT NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    subtotal_amount DECIMAL(19, 4) NOT NULL,
    discount_amount DECIMAL(19, 4) NOT NULL,
    tax_amount DECIMAL(19, 4) NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_don_hang_muc_don_hang_order
        FOREIGN KEY (order_id) REFERENCES don_hang_don_dat_hang (id) ON DELETE CASCADE,
    CONSTRAINT uk_don_hang_muc_don_hang_line UNIQUE (order_id, line_number),
    CONSTRAINT ck_don_hang_muc_don_hang_line CHECK (line_number > 0),
    CONSTRAINT ck_don_hang_muc_don_hang_quantity CHECK (quantity > 0),
    CONSTRAINT ck_don_hang_muc_don_hang_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_don_hang_muc_don_hang_subtotal CHECK (subtotal_amount = unit_price * quantity),
    CONSTRAINT ck_don_hang_muc_don_hang_discount
        CHECK (discount_amount >= 0 AND discount_amount <= subtotal_amount),
    CONSTRAINT ck_don_hang_muc_don_hang_tax CHECK (tax_amount >= 0),
    CONSTRAINT ck_don_hang_muc_don_hang_total
        CHECK (total_amount = subtotal_amount - discount_amount + tax_amount)
);

CREATE INDEX idx_don_hang_muc_don_hang_variant
    ON don_hang_muc_don_hang (product_variant_id);
