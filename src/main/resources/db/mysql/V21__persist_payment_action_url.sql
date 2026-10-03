ALTER TABLE thanh_toan_lan_thu
    ADD COLUMN action_url VARCHAR(2048);

ALTER TABLE thanh_toan_lan_thu
    DROP CHECK ck_thanh_toan_lan_thu_state;

ALTER TABLE thanh_toan_lan_thu
    ADD CONSTRAINT ck_thanh_toan_lan_thu_state CHECK (
        (status = 'CREATED'
            AND provider_reference IS NULL AND action_url IS NULL
            AND failure_code IS NULL AND completed_at IS NULL)
        OR (status = 'PENDING'
            AND provider_reference IS NOT NULL AND action_url IS NULL
            AND failure_code IS NULL AND completed_at IS NULL)
        OR (status = 'REQUIRES_ACTION'
            AND provider_reference IS NOT NULL
            AND CHAR_LENGTH(action_url) BETWEEN 9 AND 2048
            AND action_url LIKE 'https://%'
            AND failure_code IS NULL AND completed_at IS NULL)
        OR (status = 'UNKNOWN'
            AND action_url IS NULL AND failure_code IS NULL AND completed_at IS NULL)
        OR (status = 'SUCCEEDED'
            AND provider_reference IS NOT NULL AND action_url IS NULL
            AND failure_code IS NULL AND completed_at IS NOT NULL)
        OR (status = 'FAILED'
            AND action_url IS NULL AND failure_code IS NOT NULL AND completed_at IS NOT NULL)
        OR (status IN ('CANCELLED', 'EXPIRED')
            AND action_url IS NULL AND failure_code IS NULL AND completed_at IS NOT NULL)
    );
