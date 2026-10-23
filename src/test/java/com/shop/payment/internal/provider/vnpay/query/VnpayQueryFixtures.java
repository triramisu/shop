package com.shop.payment.internal.provider.vnpay.query;

import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.ObjectMapper;

final class VnpayQueryFixtures {
    static final String SECRET = "sandbox-query-test-key-01234567890123456789";
    static final String OLD_SECRET = "previous-query-test-key-0123456789012345678";
    static final UUID ATTEMPT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ORDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), ZoneOffset.UTC);
    static final ObjectMapper MAPPER = new ObjectMapper();
    // Deliberately independent of the production field list and canonical implementation.
    static final List<String> RESPONSE_ORDER = List.of(
            "vnp_ResponseId",
            "vnp_Command",
            "vnp_ResponseCode",
            "vnp_Message",
            "vnp_TmnCode",
            "vnp_TxnRef",
            "vnp_Amount",
            "vnp_BankCode",
            "vnp_PayDate",
            "vnp_TransactionNo",
            "vnp_TransactionType",
            "vnp_TransactionStatus",
            "vnp_OrderInfo",
            "vnp_PromotionCode",
            "vnp_PromotionAmount");

    static PaymentStatusQueryRequest request() {
        return new PaymentStatusQueryRequest(
                ATTEMPT, ORDER, new BigDecimal("199000"), "VND", Instant.parse("2026-10-19T01:00:00Z"));
    }

    static VnpayQueryCodec codec() {
        return new VnpayQueryCodec(
                "TEST1234", "203.0.113.10", new VnpaySignature(SECRET, List.of(OLD_SECRET)), CLOCK, MAPPER);
    }

    static Map<String, String> responseFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_ResponseId", "response1");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_Message", "Giao dịch thành công");
        fields.put("vnp_TmnCode", "TEST1234");
        fields.put("vnp_TxnRef", "11111111111111111111111111111111");
        fields.put("vnp_Amount", "19900000");
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_PayDate", "20261019080100");
        fields.put("vnp_TransactionNo", "123456");
        fields.put("vnp_TransactionType", "01");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_OrderInfo", "Thanh toan don hang 22222222-2222-2222-2222-222222222222");
        fields.put("vnp_PromotionCode", "");
        fields.put("vnp_PromotionAmount", "");
        return fields;
    }

    static byte[] signed(Map<String, String> fields, String key) {
        String data = String.join(
                "|",
                RESPONSE_ORDER.stream()
                        .map(field -> fields.getOrDefault(field, ""))
                        .toList());
        Map<String, String> signed = new LinkedHashMap<>(fields);
        signed.put("vnp_SecureHash", hmac(key, data));
        return MAPPER.writeValueAsBytes(signed);
    }

    static String hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
