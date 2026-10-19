package com.shop.payment.internal.webhook.vnpay;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;

public final class VnpayParameters {
    private VnpayParameters() {}

    public static Map<String, String> read(HttpServletRequest request) {
        if (request.getQueryString() == null || request.getQueryString().length() > 8192) {
            throw new IllegalArgumentException("Invalid VNPay query");
        }
        Map<String, String[]> input = request.getParameterMap();
        if (input.isEmpty() || input.size() > 32) {
            throw new IllegalArgumentException("Invalid VNPay parameter count");
        }
        Map<String, String> values = new TreeMap<>();
        input.forEach((key, entries) -> {
            if (!key.matches("vnp_[A-Za-z0-9]{1,40}")
                    || entries.length != 1
                    || entries[0] == null
                    || entries[0].length() > 512
                    || !entries[0].matches("[ -~]*")) {
                throw new IllegalArgumentException("Invalid VNPay parameter");
            }
            values.put(key, entries[0]);
        });
        if (values.containsKey("vnp_SecureHashType")
                && !"HmacSHA512".equalsIgnoreCase(values.get("vnp_SecureHashType"))) {
            throw new IllegalArgumentException("Unsupported VNPay signature algorithm");
        }
        return Map.copyOf(values);
    }
}
