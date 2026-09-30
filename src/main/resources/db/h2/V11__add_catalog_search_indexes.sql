CREATE INDEX idx_san_pham_san_pham_hien_hanh_tao
    ON san_pham_san_pham (deleted_at, created_at, id);

CREATE INDEX idx_san_pham_san_pham_hien_hanh_cap_nhat
    ON san_pham_san_pham (deleted_at, updated_at, id);

CREATE INDEX idx_san_pham_san_pham_hien_hanh_ten
    ON san_pham_san_pham (deleted_at, name, id);

CREATE INDEX idx_san_pham_san_pham_danh_muc_trang_thai_tao
    ON san_pham_san_pham (category_id, status, deleted_at, created_at, id);
