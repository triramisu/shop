CREATE TABLE identity_refresh_tokens (
    jti VARCHAR(36) NOT NULL,
    user_id BINARY(16) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    revoked_at TIMESTAMP(6),
    replaced_by_jti VARCHAR(36),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (jti),
    CONSTRAINT fk_identity_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES identity_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_identity_refresh_tokens_user ON identity_refresh_tokens (user_id);
CREATE INDEX idx_identity_refresh_tokens_family ON identity_refresh_tokens (family_id);
CREATE INDEX idx_identity_refresh_tokens_expires ON identity_refresh_tokens (expires_at);
