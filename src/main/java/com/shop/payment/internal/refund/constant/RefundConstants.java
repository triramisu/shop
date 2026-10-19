package com.shop.payment.internal.refund.constant;

public final class RefundConstants {
    public static final String AUTHORITY = "PAYMENT_REFUND";
    public static final String ADMIN_BASE = "/api/admin/payments/{paymentAttemptId}/refunds";
    public static final String PROVIDER_KEY_PREFIX = "refund-";

    private RefundConstants() {}
}
