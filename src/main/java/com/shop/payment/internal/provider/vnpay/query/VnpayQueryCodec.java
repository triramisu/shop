package com.shop.payment.internal.provider.vnpay.query;

import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderStatus;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

final class VnpayQueryCodec {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final List<String> RESPONSE_FIELDS = List.of(
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
    private final String tmnCode;
    private final String serverIp;
    private final VnpaySignature signature;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final ObjectReader reader;

    VnpayQueryCodec(String tmnCode, String serverIp, VnpaySignature signature, Clock clock, ObjectMapper mapper) {
        this.tmnCode = tmnCode;
        this.serverIp = serverIp;
        this.signature = signature;
        this.clock = clock;
        this.mapper = mapper;
        this.reader = mapper.readerFor(JsonNode.class)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    String requestBody(PaymentStatusQueryRequest request) {
        VnpayPaymentProvider.amount(request.amount(), request.currency());
        String transactionDate;
        try {
            transactionDate = DATE.format(request.createdAt());
        } catch (DateTimeException exception) {
            throw new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "VNPAY_QUERY_DATE_INVALID");
        }
        if (request.createdAt().isAfter(clock.instant()) || !transactionDate.matches("[0-9]{14}")) {
            throw new PaymentProviderException(PaymentProviderErrorType.INVALID_REQUEST, "VNPAY_QUERY_DATE_INVALID");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", request.queryId().toString().replace("-", ""));
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_TxnRef", VnpayPaymentProvider.reference(request.paymentAttemptId()));
        fields.put("vnp_TransactionDate", transactionDate);
        fields.put("vnp_CreateDate", DATE.format(clock.instant()));
        fields.put("vnp_IpAddr", serverIp);
        fields.put("vnp_OrderInfo", VnpayPaymentProvider.orderInfo(request.orderId()));
        fields.put("vnp_SecureHash", signature.signData(String.join("|", fields.values())));
        return mapper.writeValueAsString(fields);
    }

    PaymentStatusQueryResult response(byte[] bytes, PaymentStatusQueryRequest request) {
        Map<String, String> fields = parse(bytes);
        String data = String.join("|", RESPONSE_FIELDS.stream().map(fields::get).toList());
        if (!signature.verifyData(data, fields.get("vnp_SecureHash"))) {
            throw protocol("VNPAY_QUERY_SIGNATURE_INVALID");
        }
        require(fields.get("vnp_ResponseId").matches("[A-Za-z0-9]{1,32}"));
        String command = fields.get("vnp_Command");
        require(command.isEmpty() || command.equals("querydr"));
        require(fields.get("vnp_ResponseCode").matches("[0-9]{2}"));
        require(fields.get("vnp_Message").length() >= 10);
        require(tmnCode.equals(fields.get("vnp_TmnCode")));
        require(VnpayPaymentProvider.reference(request.paymentAttemptId()).equals(fields.get("vnp_TxnRef")));
        if (!"00".equals(fields.get("vnp_ResponseCode"))) {
            throw protocol("VNPAY_QUERY_REJECTED");
        }
        require(VnpayPaymentProvider.amount(request.amount(), request.currency())
                .equals(fields.get("vnp_Amount")));
        require(VnpayPaymentProvider.orderInfo(request.orderId()).equals(fields.get("vnp_OrderInfo")));
        require("01".equals(fields.get("vnp_TransactionType")));
        require(fields.get("vnp_TransactionNo").matches("[0-9]{1,15}"));
        require(fields.get("vnp_BankCode").matches("[A-Za-z0-9]{3,20}"));
        require(fields.get("vnp_TransactionStatus").matches("[0-9]{2}"));
        require(fields.get("vnp_PromotionAmount").matches("[0-9]{0,12}"));
        require(fields.get("vnp_PromotionCode").matches("[0-9]{0,12}"));
        String payDate = fields.get("vnp_PayDate");
        if (!payDate.isEmpty()) {
            try {
                require(payDate.matches("[0-9]{14}"));
                LocalDateTime.parse(payDate, DATE);
            } catch (DateTimeParseException exception) {
                throw protocol("VNPAY_QUERY_RESPONSE_INVALID");
            }
        }
        PaymentProviderStatus status =
                switch (fields.get("vnp_TransactionStatus")) {
                    case "00" -> PaymentProviderStatus.SUCCEEDED;
                    case "01" -> PaymentProviderStatus.PENDING;
                    case "02" -> PaymentProviderStatus.FAILED;
                    default -> PaymentProviderStatus.UNKNOWN;
                };
        return new PaymentStatusQueryResult(
                status,
                fields.get("vnp_TxnRef"),
                fields.get("vnp_TransactionNo"),
                clock.instant(),
                fields.get("vnp_ResponseId"),
                fingerprint(data));
    }

    private static String fingerprint(String data) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Map<String, String> parse(byte[] bytes) {
        try {
            JsonNode root = reader.readTree(bytes);
            require(root != null && root.isObject() && root.size() <= RESPONSE_FIELDS.size() + 1);
            Map<String, String> fields = new LinkedHashMap<>();
            for (var entry : root.properties()) {
                require(RESPONSE_FIELDS.contains(entry.getKey())
                        || entry.getKey().equals("vnp_SecureHash"));
                require(entry.getValue().isString());
            }
            for (String field : RESPONSE_FIELDS) {
                JsonNode node = root.get(field);
                String value = node == null ? "" : node.asString();
                require(value.length() <= 255
                        && value.codePoints().noneMatch(c -> c == '|' || Character.isISOControl(c)));
                fields.put(field, value);
            }
            JsonNode hash = root.get("vnp_SecureHash");
            require(hash != null && hash.asString().matches("[a-fA-F0-9]{128}"));
            fields.put("vnp_SecureHash", hash.asString());
            return fields;
        } catch (PaymentProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Never attach the parser exception: it may contain raw merchant response data.
            throw protocol("VNPAY_QUERY_JSON_INVALID");
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw protocol("VNPAY_QUERY_RESPONSE_INVALID");
        }
    }

    static PaymentProviderException protocol(String code) {
        return new PaymentProviderException(PaymentProviderErrorType.PROTOCOL_ERROR, code);
    }
}
