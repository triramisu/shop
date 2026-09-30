INSERT INTO xac_thuc_quyen_han (code, description)
VALUES ('CATALOG_READ', 'Xem toàn bộ danh mục và sản phẩm trong phân hệ quản trị'),
       ('CATALOG_WRITE', 'Tạo và cập nhật danh mục, sản phẩm, biến thể'),
       ('CATALOG_PUBLISH', 'Công bố hoặc ẩn sản phẩm'),
       ('CATALOG_IMAGE_MANAGE', 'Tải lên, sắp xếp và xóa ảnh sản phẩm');

INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
VALUES ('ADMIN', 'CATALOG_READ'),
       ('ADMIN', 'CATALOG_WRITE'),
       ('ADMIN', 'CATALOG_PUBLISH'),
       ('ADMIN', 'CATALOG_IMAGE_MANAGE'),
       ('STAFF', 'CATALOG_READ'),
       ('STAFF', 'CATALOG_WRITE'),
       ('STAFF', 'CATALOG_PUBLISH'),
       ('STAFF', 'CATALOG_IMAGE_MANAGE');

UPDATE xac_thuc_phien_lam_moi
SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL;
