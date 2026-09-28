CREATE TABLE san_pham_danh_muc (
    id BINARY(16) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_san_pham_danh_muc_code UNIQUE (code),
    CONSTRAINT uk_san_pham_danh_muc_slug UNIQUE (slug),
    CONSTRAINT ck_san_pham_danh_muc_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_san_pham_danh_muc_soft_delete CHECK (deleted_at IS NULL OR status = 'INACTIVE')
);

CREATE TABLE san_pham_san_pham (
    id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description VARCHAR(5000),
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_san_pham_san_pham_slug UNIQUE (slug),
    CONSTRAINT fk_san_pham_san_pham_danh_muc
        FOREIGN KEY (category_id) REFERENCES san_pham_danh_muc (id) ON DELETE RESTRICT,
    CONSTRAINT ck_san_pham_san_pham_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN', 'ARCHIVED')),
    CONSTRAINT ck_san_pham_san_pham_soft_delete CHECK (deleted_at IS NULL OR status = 'ARCHIVED')
);

CREATE TABLE san_pham_bien_the (
    id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_san_pham_bien_the_sku UNIQUE (sku),
    CONSTRAINT fk_san_pham_bien_the_san_pham
        FOREIGN KEY (product_id) REFERENCES san_pham_san_pham (id) ON DELETE RESTRICT,
    CONSTRAINT ck_san_pham_bien_the_price CHECK (price >= 0),
    CONSTRAINT ck_san_pham_bien_the_currency CHECK (CHAR_LENGTH(currency) = 3),
    CONSTRAINT ck_san_pham_bien_the_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_san_pham_bien_the_soft_delete CHECK (deleted_at IS NULL OR status = 'ARCHIVED')
);

CREATE INDEX idx_san_pham_san_pham_category ON san_pham_san_pham (category_id, deleted_at);
CREATE INDEX idx_san_pham_san_pham_status ON san_pham_san_pham (status, deleted_at);
CREATE INDEX idx_san_pham_bien_the_product ON san_pham_bien_the (product_id, deleted_at);
CREATE INDEX idx_san_pham_bien_the_status ON san_pham_bien_the (status, deleted_at);
