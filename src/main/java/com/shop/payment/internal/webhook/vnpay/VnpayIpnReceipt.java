package com.shop.payment.internal.webhook.vnpay;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnReceipt(
        @JsonProperty("RspCode") String code,
        @JsonProperty("Message") String message) {
    static VnpayIpnReceipt of(String code) {
        String message =
                switch (code) {
                    case "00" -> "Confirm Success";
                    case "01" -> "Order not found";
                    case "02" -> "Order already confirmed";
                    case "04" -> "Invalid amount";
                    case "97" -> "Invalid signature";
                    default -> "Unable to process notification";
                };
        return new VnpayIpnReceipt(code, message);
    }
}
