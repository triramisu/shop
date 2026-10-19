package com.shop.payment.provider;

import java.util.Objects;

public record RefundProviderResult(RefundProviderStatus status, String providerReference, String failureCode) {
    public RefundProviderResult {
        Objects.requireNonNull(status);
        if ((status == RefundProviderStatus.PENDING || status == RefundProviderStatus.SUCCEEDED)
                && providerReference == null) {
            throw new IllegalArgumentException("Thiếu mã hoàn tiền của provider");
        }
        if (providerReference != null && !providerReference.matches("[A-Za-z0-9_-]{1,150}")) {
            throw new IllegalArgumentException("Mã hoàn tiền của provider không hợp lệ");
        }
        if (status == RefundProviderStatus.FAILED
                ? failureCode == null || !failureCode.matches("[A-Z0-9][A-Z0-9_.-]{0,99}")
                : failureCode != null) {
            throw new IllegalArgumentException("Mã lỗi hoàn tiền không hợp lệ");
        }
    }
}
