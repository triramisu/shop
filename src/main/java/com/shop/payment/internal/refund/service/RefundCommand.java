package com.shop.payment.internal.refund.service;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

record RefundCommand(BigDecimal amount, String currency, String keyHash, String fingerprint) {
    static RefundCommand of(BigDecimal amount, String currency, String key) {
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_KEY_INVALID);
        }
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_DATA_INVALID);
        }
        BigDecimal normalized = null;
        if (amount != null) {
            try {
                normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new AppException(ErrorCode.PAYMENT_REFUND_DATA_INVALID);
            }
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw new AppException(ErrorCode.PAYMENT_REFUND_DATA_INVALID);
            }
        }
        String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        return new RefundCommand(
                normalized,
                normalizedCurrency,
                hash(key),
                hash("v1|" + normalizedCurrency + "|" + (normalized == null ? "FULL" : normalized.toPlainString())));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Không có thuật toán băm yêu cầu", exception);
        }
    }
}
