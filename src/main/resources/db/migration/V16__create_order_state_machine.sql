CREATE TABLE don_hang_don_dat_hang (
    id BINARY(16) NOT NULL,
    owner_subject VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    status_changed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_don_hang_don_dat_hang_status
        CHECK (status IN ('PENDING', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

CREATE INDEX idx_don_hang_don_dat_hang_owner_created
    ON don_hang_don_dat_hang (owner_subject, created_at);

CREATE INDEX idx_don_hang_don_dat_hang_status_updated
    ON don_hang_don_dat_hang (status, updated_at);
