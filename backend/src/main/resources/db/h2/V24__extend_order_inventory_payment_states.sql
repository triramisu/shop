ALTER TABLE don_hang_dieu_phoi_ton_kho
    DROP CONSTRAINT ck_don_hang_dieu_phoi_ton_kho_status;

ALTER TABLE don_hang_dieu_phoi_ton_kho
    ADD CONSTRAINT ck_don_hang_dieu_phoi_ton_kho_status CHECK (
        status IN (
            'REQUESTED', 'PROCESSING', 'RESERVED', 'RETRY_REQUIRED',
            'COMPENSATING', 'FAILED', 'COMPENSATION_REQUIRED',
            'PAYMENT_PENDING', 'PAYMENT_CONFIRMING', 'PAYMENT_CONFIRMED',
            'PAYMENT_RELEASING', 'PAYMENT_RELEASED', 'PAYMENT_RECOVERY_REQUIRED'
        )
    );

ALTER TABLE don_hang_dong_giu_ton_kho
    DROP CONSTRAINT ck_don_hang_dong_giu_ton_kho_status;

ALTER TABLE don_hang_dong_giu_ton_kho
    ADD CONSTRAINT ck_don_hang_dong_giu_ton_kho_status
        CHECK (status IN ('PENDING', 'RESERVED', 'CONFIRMED', 'RELEASED', 'FAILED'));
