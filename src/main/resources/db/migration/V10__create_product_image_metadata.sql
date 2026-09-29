CREATE TABLE san_pham_hinh_anh (
    id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    primary_image BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_san_pham_hinh_anh_object_key UNIQUE (object_key),
    CONSTRAINT fk_san_pham_hinh_anh_san_pham
        FOREIGN KEY (product_id) REFERENCES san_pham_san_pham (id) ON DELETE RESTRICT,
    CONSTRAINT ck_san_pham_hinh_anh_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_san_pham_hinh_anh_size CHECK (size_bytes > 0),
    CONSTRAINT ck_san_pham_hinh_anh_order CHECK (display_order >= 0)
);

CREATE INDEX idx_san_pham_hinh_anh_product_order
    ON san_pham_hinh_anh (product_id, display_order);
CREATE INDEX idx_san_pham_hinh_anh_product_primary
    ON san_pham_hinh_anh (product_id, primary_image);
