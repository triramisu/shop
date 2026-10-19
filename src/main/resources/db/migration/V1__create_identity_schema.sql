CREATE TABLE identity_permissions (
    code VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    PRIMARY KEY (code)
);

CREATE TABLE identity_roles (
    code VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    PRIMARY KEY (code)
);

CREATE TABLE identity_users (
    id BINARY(16) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    date_of_birth DATE,
    status VARCHAR(30) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_identity_users_username UNIQUE (username),
    CONSTRAINT uk_identity_users_email UNIQUE (email),
    CONSTRAINT ck_identity_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE identity_role_permissions (
    role_code VARCHAR(50) NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_identity_role_permissions_role
        FOREIGN KEY (role_code) REFERENCES identity_roles (code) ON DELETE CASCADE,
    CONSTRAINT fk_identity_role_permissions_permission
        FOREIGN KEY (permission_code) REFERENCES identity_permissions (code) ON DELETE CASCADE
);

CREATE TABLE identity_user_roles (
    user_id BINARY(16) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_identity_user_roles_user
        FOREIGN KEY (user_id) REFERENCES identity_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_identity_user_roles_role
        FOREIGN KEY (role_code) REFERENCES identity_roles (code) ON DELETE RESTRICT
);

CREATE INDEX idx_identity_user_roles_role ON identity_user_roles (role_code);
CREATE INDEX idx_identity_role_permissions_permission ON identity_role_permissions (permission_code);

INSERT INTO identity_roles (code, description)
VALUES ('ADMIN', 'Platform administrator'),
       ('USER', 'Registered customer');
