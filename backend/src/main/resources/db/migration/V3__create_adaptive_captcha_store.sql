CREATE TABLE identity_login_failures (
    principal_hash CHAR(64) NOT NULL,
    failure_count INT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (principal_hash)
);

CREATE INDEX idx_identity_login_failures_expires
    ON identity_login_failures (expires_at);

CREATE TABLE identity_captcha_challenges (
    captcha_id VARCHAR(36) NOT NULL,
    principal_hash CHAR(64) NOT NULL,
    answer_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (captcha_id)
);

CREATE INDEX idx_identity_captcha_principal
    ON identity_captcha_challenges (principal_hash);
CREATE INDEX idx_identity_captcha_expires
    ON identity_captcha_challenges (expires_at);
