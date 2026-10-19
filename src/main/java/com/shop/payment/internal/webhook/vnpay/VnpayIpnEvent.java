package com.shop.payment.internal.webhook.vnpay;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record VnpayIpnEvent(
        UUID attemptId,
        String reference,
        String transactionNo,
        String amount,
        String orderInfo,
        String responseCode,
        String transactionStatus,
        Instant paidAt) {
    static VnpayIpnEvent parse(Map<String, String> p, Instant receivedAt) {
        String ref = required(p, "vnp_TxnRef", "[a-f0-9]{32}");
        UUID id = UUID.fromString(ref.substring(0, 8) + "-" + ref.substring(8, 12) + "-" + ref.substring(12, 16) + "-"
                + ref.substring(16, 20) + "-" + ref.substring(20));
        if (p.containsKey("vnp_CurrCode") && !"VND".equals(p.get("vnp_CurrCode"))) {
            throw new IllegalArgumentException("Invalid VNPay currency");
        }
        required(p, "vnp_BankCode", "[A-Za-z0-9]{3,20}");
        Instant paidAt = receivedAt;
        if (p.containsKey("vnp_PayDate") && !p.get("vnp_PayDate").isEmpty()) {
            paidAt = LocalDateTime.parse(
                            required(p, "vnp_PayDate", "[0-9]{14}"),
                            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT))
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toInstant();
        }
        return new VnpayIpnEvent(
                id,
                ref,
                required(p, "vnp_TransactionNo", "[0-9]{1,15}"),
                required(p, "vnp_Amount", "[0-9]{1,12}"),
                required(p, "vnp_OrderInfo", "[ -~]{1,255}"),
                required(p, "vnp_ResponseCode", "[0-9]{2}"),
                required(p, "vnp_TransactionStatus", "[0-9]{2}"),
                paidAt);
    }

    boolean succeeded() {
        return responseCode.equals("00") && transactionStatus.equals("00");
    }

    boolean definitive() {
        // 07 can mean funds debited but suspected fraud; unknown results must not release stock.
        return succeeded()
                || (transactionStatus.equals("02")
                        && Set.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79")
                                .contains(responseCode));
    }

    String eventId() {
        return reference + ":" + transactionNo + ":" + responseCode + ":" + transactionStatus;
    }

    private static String required(Map<String, String> p, String key, String pattern) {
        String value = p.get(key);
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException("Invalid VNPay notification field");
        }
        return value;
    }
}
