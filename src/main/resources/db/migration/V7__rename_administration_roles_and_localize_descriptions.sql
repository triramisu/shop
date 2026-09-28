INSERT INTO xac_thuc_vai_tro (code, description, system_role)
SELECT 'STAFF', 'Nhân viên quản trị', TRUE
FROM xac_thuc_vai_tro
WHERE code = 'ADMIN';

INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
SELECT 'STAFF', permission_code
FROM xac_thuc_vai_tro_quyen_han
WHERE role_code = 'ADMIN';

INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code)
SELECT user_id, 'STAFF'
FROM xac_thuc_nguoi_dung_vai_tro
WHERE role_code = 'ADMIN';

DELETE FROM xac_thuc_nguoi_dung_vai_tro
WHERE role_code = 'ADMIN';

DELETE FROM xac_thuc_vai_tro_quyen_han
WHERE role_code = 'ADMIN';

DELETE FROM xac_thuc_vai_tro
WHERE code = 'ADMIN';

INSERT INTO xac_thuc_vai_tro (code, description, system_role)
SELECT 'ADMIN', 'Quản trị viên cao nhất', TRUE
FROM xac_thuc_vai_tro
WHERE code = 'SUPER_ADMIN';

INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
SELECT 'ADMIN', permission_code
FROM xac_thuc_vai_tro_quyen_han
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code)
SELECT user_id, 'ADMIN'
FROM xac_thuc_nguoi_dung_vai_tro
WHERE role_code = 'SUPER_ADMIN';

DELETE FROM xac_thuc_nguoi_dung_vai_tro
WHERE role_code = 'SUPER_ADMIN';

DELETE FROM xac_thuc_vai_tro_quyen_han
WHERE role_code = 'SUPER_ADMIN';

DELETE FROM xac_thuc_vai_tro
WHERE code = 'SUPER_ADMIN';

UPDATE xac_thuc_vai_tro
SET description = 'Người dùng'
WHERE code = 'USER';

UPDATE xac_thuc_quyen_han
SET description = CASE code
    WHEN 'SYSTEM_USER_READ' THEN 'Xem danh sách người dùng trong phân hệ quản trị'
    WHEN 'SYSTEM_USER_STATUS_UPDATE' THEN 'Khóa, mở khóa, kích hoạt hoặc vô hiệu hóa người dùng'
    WHEN 'SYSTEM_USER_ROLE_ASSIGN' THEN 'Phân vai trò cho người dùng'
    WHEN 'SYSTEM_ROLE_READ' THEN 'Xem danh mục vai trò'
    WHEN 'SYSTEM_ROLE_MANAGE' THEN 'Tạo, sửa và xóa vai trò tùy chỉnh'
    WHEN 'SYSTEM_PERMISSION_READ' THEN 'Xem danh mục quyền hệ thống'
    ELSE description
END
WHERE code IN (
    'SYSTEM_USER_READ',
    'SYSTEM_USER_STATUS_UPDATE',
    'SYSTEM_USER_ROLE_ASSIGN',
    'SYSTEM_ROLE_READ',
    'SYSTEM_ROLE_MANAGE',
    'SYSTEM_PERMISSION_READ'
);

UPDATE xac_thuc_phien_lam_moi
SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL;
