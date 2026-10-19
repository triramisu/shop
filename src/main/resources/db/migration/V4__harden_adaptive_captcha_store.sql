CREATE TABLE identity_captcha_store_locks (
    lock_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (lock_name)
);

INSERT INTO identity_captcha_store_locks (lock_name)
VALUES ('challenge-issuance');

DELETE FROM identity_captcha_challenges
WHERE captcha_id IN (
    SELECT captcha_id
    FROM (
        SELECT
            captcha_id,
            ROW_NUMBER() OVER (
                PARTITION BY principal_hash
                ORDER BY created_at DESC, captcha_id DESC
            ) AS duplicate_rank
        FROM identity_captcha_challenges
    ) ranked_challenges
    WHERE duplicate_rank > 1
);

ALTER TABLE identity_captcha_challenges
    ADD CONSTRAINT uk_identity_captcha_principal UNIQUE (principal_hash);
