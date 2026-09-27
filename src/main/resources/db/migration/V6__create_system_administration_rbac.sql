ALTER TABLE xac_thuc_vai_tro
    ADD COLUMN system_role BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE xac_thuc_vai_tro
SET system_role = TRUE
WHERE code IN ('ADMIN', 'USER');

INSERT INTO xac_thuc_vai_tro (code, description, system_role)
VALUES ('SUPER_ADMIN', 'Top-level system administrator', TRUE);

INSERT INTO xac_thuc_quyen_han (code, description)
VALUES ('SYSTEM_USER_READ', 'View users in system administration'),
       ('SYSTEM_USER_STATUS_UPDATE', 'Lock, unlock, enable, or disable users'),
       ('SYSTEM_USER_ROLE_ASSIGN', 'Assign roles to users'),
       ('SYSTEM_ROLE_READ', 'View the role catalog'),
       ('SYSTEM_ROLE_MANAGE', 'Create, update, and delete custom roles'),
       ('SYSTEM_PERMISSION_READ', 'View the permission catalog');

INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
VALUES ('SUPER_ADMIN', 'SYSTEM_USER_READ'),
       ('SUPER_ADMIN', 'SYSTEM_USER_STATUS_UPDATE'),
       ('SUPER_ADMIN', 'SYSTEM_USER_ROLE_ASSIGN'),
       ('SUPER_ADMIN', 'SYSTEM_ROLE_READ'),
       ('SUPER_ADMIN', 'SYSTEM_ROLE_MANAGE'),
       ('SUPER_ADMIN', 'SYSTEM_PERMISSION_READ');

INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code)
VALUES ('ADMIN', 'SYSTEM_USER_READ'),
       ('ADMIN', 'SYSTEM_USER_STATUS_UPDATE'),
       ('ADMIN', 'SYSTEM_USER_ROLE_ASSIGN'),
       ('ADMIN', 'SYSTEM_ROLE_READ'),
       ('ADMIN', 'SYSTEM_PERMISSION_READ');
