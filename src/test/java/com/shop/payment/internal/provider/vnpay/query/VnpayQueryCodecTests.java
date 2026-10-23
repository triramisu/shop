package com.shop.payment.internal.provider.vnpay.query;

import static com.shop.payment.internal.provider.vnpay.query.VnpayQueryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.provider.PaymentProviderErrorType;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderStatus;
import com.shop.payment.provider.PaymentStatusQueryRequest;
import com.shop.payment.provider.PaymentStatusQueryResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VnpayQueryCodecTests {
    @Test
    void matchesIndependentNodeCryptoGoldenVectorsWithoutUrlEncoding() {
        String data =
                "a1b2c3|2.1.0|querydr|TEST1234|11111111111111111111111111111111|20261019080000|20261019090000|203.0.113.10|Thanh toan don hang 22222222-2222-2222-2222-222222222222";
        assertThat(new VnpaySignature(SECRET).signData(data))
                .isEqualTo(
                        "da1ea323541facbf0291576bc66d1642e53bc40183d2075ddc7a104350c683041fd36c60a9ae6722a47014fb5564749b0d2142283359184ba27b2e87721445e1");
        var fields = responseFields();
        String responseData =
                String.join("|", RESPONSE_ORDER.stream().map(fields::get).toList());
        assertThat(new VnpaySignature(SECRET).signData(responseData))
                .isEqualTo(
                        "e5fc72965c7c3949d437af3343c216f0301bce654e92ce0046f3094a4d85e5d8a5fb5ae0d8941aa39bf9f094122317de35e5570ac8dd80d484e35efa0bdcc802");
    }

    @Test
    void signsRequestWithOriginalCheckoutDateAndUniqueRequestId() {
        var root = MAPPER.readTree(codec().requestBody(request()));
        assertThat(root.size()).isEqualTo(10);
        assertThat(root.get("vnp_RequestId").asString()).matches("[a-f0-9]{32}");
        assertThat(root.get("vnp_Version").asString()).isEqualTo("2.1.0");
        assertThat(root.get("vnp_Command").asString()).isEqualTo("querydr");
        assertThat(root.get("vnp_TransactionDate").asString()).isEqualTo("20261019080000");
        assertThat(root.get("vnp_CreateDate").asString()).isEqualTo("20261019090000");
        assertThat(root.get("vnp_IpAddr").asString()).isEqualTo("203.0.113.10");
        String data = root.get("vnp_RequestId").asString()
                + "|2.1.0|querydr|TEST1234|11111111111111111111111111111111|20261019080000|20261019090000|203.0.113.10|Thanh toan don hang 22222222-2222-2222-2222-222222222222";
        assertThat(root.get("vnp_SecureHash").asString()).isEqualTo(hmac(SECRET, data));
        assertThat(MAPPER.readTree(codec().requestBody(request()))
                        .get("vnp_RequestId")
                        .asString())
                .isNotEqualTo(root.get("vnp_RequestId").asString());
    }

    @ParameterizedTest
    @CsvSource({
        "00,SUCCEEDED",
        "01,PENDING",
        "02,FAILED",
        "04,UNKNOWN",
        "05,UNKNOWN",
        "06,UNKNOWN",
        "07,UNKNOWN",
        "09,UNKNOWN",
        "99,UNKNOWN"
    })
    void querySuccessCodeAloneDoesNotMeanPaymentSuccess(String code, PaymentProviderStatus expected) {
        var fields = responseFields();
        fields.put("vnp_TransactionStatus", code);
        var result = codec().response(signed(fields, SECRET), request());
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.providerReference()).isEqualTo("11111111111111111111111111111111");
        assertThat(result.providerTransactionNo()).isEqualTo("123456");
        assertThat(result.observedAt()).isEqualTo(CLOCK.instant());
        assertThat(result.toString()).doesNotContain("123456", result.providerReference(), SECRET);
    }

    @Test
    void supportsDocumentedOptionalResponseFieldsAndRotatedSignature() {
        var fields = responseFields();
        fields.remove("vnp_Command");
        fields.remove("vnp_PayDate");
        fields.remove("vnp_PromotionCode");
        fields.remove("vnp_PromotionAmount");
        assertThat(codec().response(signed(fields, OLD_SECRET), request()).status())
                .isEqualTo(PaymentProviderStatus.SUCCEEDED);
    }

    @ParameterizedTest
    @CsvSource({
        "vnp_TmnCode,EVIL1234",
        "vnp_TxnRef,22222222222222222222222222222222",
        "vnp_Amount,19900100",
        "vnp_OrderInfo,another-order",
        "vnp_TransactionType,02",
        "vnp_TransactionNo,x",
        "vnp_ResponseId,bad-id",
        "vnp_Command,refund",
        "vnp_BankCode,x",
        "vnp_TransactionStatus,success",
        "vnp_PromotionAmount,-1",
        "vnp_PayDate,20260230080000",
        "vnp_PayDate,x",
        "vnp_ResponseCode,success",
        "vnp_Message,short",
        "vnp_PromotionCode,abc",
        "vnp_BankCode,''",
        "vnp_TransactionNo,''"
    })
    void rejectsSignedButMismatchedOrInvalidResponses(String field, String value) {
        var fields = responseFields();
        fields.put(field, value);
        protocol(signed(fields, SECRET));
    }

    @ParameterizedTest
    @ValueSource(strings = {"91", "94", "97", "02", "03", "99"})
    void queryRejectionIsNeverConvertedIntoFailedPayment(String code) {
        var fields = responseFields();
        fields.put("vnp_ResponseCode", code);
        assertThatThrownBy(() -> codec().response(signed(fields, SECRET), request()))
                .isInstanceOfSatisfying(
                        PaymentProviderException.class,
                        error -> assertThat(error.getProviderErrorCode()).isEqualTo("VNPAY_QUERY_REJECTED"));
    }

    @Test
    void rejectsTamperingWrongKeyAndMissingHashWithoutLeakingParserData() {
        byte[] valid = signed(responseFields(), SECRET);
        protocol(new String(valid, StandardCharsets.UTF_8)
                .replace("19900000", "29900000")
                .getBytes(StandardCharsets.UTF_8));
        protocol(signed(responseFields(), "untrusted-key-012345678901234567890123456"));
        protocol(MAPPER.writeValueAsBytes(responseFields()));
        protocol("{\"vnp_SecureHash\":\"secret-that-must-not-leak\"".getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "null",
                "[]",
                "false",
                "1",
                "{\"vnp_Amount\":1}",
                "{\"vnp_Amount\":null}",
                "{\"vnp_Amount\":{}}",
                "{\"vnp_Amount\":\"1\",\"vnp_Amount\":\"2\"}",
                "{\"unexpected\":\"field\"}"
            })
    void rejectsInvalidJsonShapesAndDuplicateFields(String json) {
        protocol(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsTrailingTokensExcessFieldsPipeControlsAndOversizedFields() {
        byte[] valid = signed(responseFields(), SECRET);
        protocol((new String(valid, StandardCharsets.UTF_8) + " {}").getBytes(StandardCharsets.UTF_8));
        protocol((new String(valid, StandardCharsets.UTF_8) + " garbage").getBytes(StandardCharsets.UTF_8));
        var fields = responseFields();
        fields.put("extra", "data");
        protocol(signed(fields, SECRET));
        for (String value : List.of("a|b", "a\nb", "a\u0000b", "a".repeat(256))) {
            fields = responseFields();
            fields.put("vnp_Message", value);
            protocol(signed(fields, SECRET));
        }
    }

    @Test
    void rejectsDuplicateOrWrongScalarTypeEvenWhenSignatureWouldOtherwiseBeValid() {
        String valid = new String(signed(responseFields(), SECRET), StandardCharsets.UTF_8);
        protocol(("{\"vnp_Amount\":\"19900000\"," + valid.substring(1)).getBytes(StandardCharsets.UTF_8));
        protocol(valid.replace("\"vnp_Amount\":\"19900000\"", "\"vnp_Amount\":19900000")
                .getBytes(StandardCharsets.UTF_8));
        protocol(valid.replace("\"vnp_TmnCode\":\"TEST1234\"", "\"vnp_TmnCode\":true")
                .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsUnrepresentableOriginalDatesAndMissingRequiredSignedFields() {
        assertThatThrownBy(() -> codec().requestBody(
                                new PaymentStatusQueryRequest(ATTEMPT, ORDER, BigDecimal.ONE, "VND", Instant.MIN)))
                .isInstanceOf(PaymentProviderException.class);
        assertThatThrownBy(() -> codec().requestBody(new PaymentStatusQueryRequest(
                        ATTEMPT, ORDER, BigDecimal.ONE, "VND", Instant.parse("-0001-01-01T00:00:00Z"))))
                .isInstanceOf(PaymentProviderException.class);
        for (String required : List.of(
                "vnp_ResponseId",
                "vnp_Message",
                "vnp_TmnCode",
                "vnp_TxnRef",
                "vnp_Amount",
                "vnp_TransactionType",
                "vnp_TransactionStatus",
                "vnp_OrderInfo",
                "vnp_TransactionNo",
                "vnp_BankCode")) {
            var fields = responseFields();
            fields.remove(required);
            protocol(signed(fields, SECRET));
        }
    }

    @Test
    void rejectsFutureCheckoutDateForeignCurrencyAndFractionalVnd() {
        for (var input : List.of(
                new PaymentStatusQueryRequest(
                        ATTEMPT, ORDER, BigDecimal.ONE, "VND", CLOCK.instant().plusSeconds(1)),
                new PaymentStatusQueryRequest(
                        ATTEMPT, ORDER, BigDecimal.ONE, "USD", request().createdAt()),
                new PaymentStatusQueryRequest(
                        ATTEMPT, ORDER, new BigDecimal("1.01"), "VND", request().createdAt()))) {
            assertThatThrownBy(() -> codec().requestBody(input))
                    .isInstanceOfSatisfying(PaymentProviderException.class, error -> assertThat(error.getErrorType())
                            .isEqualTo(PaymentProviderErrorType.INVALID_REQUEST));
        }
    }

    @Test
    void queryContractsEnforceRequiredTrustedValuesAndRedactIdentifiers() {
        assertThat(request().amount()).isEqualTo(new BigDecimal("199000.00"));
        assertThat(request().toString()).doesNotContain(ATTEMPT.toString(), ORDER.toString(), "199000");
        assertThatThrownBy(() -> new PaymentStatusQueryRequest(null, ORDER, BigDecimal.ONE, "VND", Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentStatusQueryRequest(ATTEMPT, null, BigDecimal.ONE, "VND", Instant.EPOCH))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentStatusQueryRequest(ATTEMPT, ORDER, BigDecimal.ONE, "VND", null))
                .isInstanceOf(NullPointerException.class);
        for (BigDecimal amount :
                List.of(BigDecimal.ZERO, BigDecimal.ONE.negate(), new BigDecimal("10000000000000000000"))) {
            assertThatThrownBy(() -> new PaymentStatusQueryRequest(ATTEMPT, ORDER, amount, "VND", Instant.EPOCH))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String currency : List.of("", "vnd", "VN")) {
            assertThatThrownBy(() ->
                            new PaymentStatusQueryRequest(ATTEMPT, ORDER, BigDecimal.ONE, currency, Instant.EPOCH))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PaymentStatusQueryRequest(ATTEMPT, ORDER, BigDecimal.ONE, null, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PaymentStatusQueryResult(PaymentProviderStatus.REQUIRES_ACTION, "ref", "1", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentStatusQueryResult(PaymentProviderStatus.UNKNOWN, null, "1", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PaymentStatusQueryResult(PaymentProviderStatus.UNKNOWN, "ref", null, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void protocol(byte[] bytes) {
        assertThatThrownBy(() -> codec().response(bytes, request()))
                .isInstanceOfSatisfying(PaymentProviderException.class, error -> {
                    assertThat(error.getErrorType()).isEqualTo(PaymentProviderErrorType.PROTOCOL_ERROR);
                    assertThat(error.getMessage()).doesNotContain(SECRET, "199000", "secret-that-must-not-leak");
                    assertThat(error.getCause()).isNull();
                });
    }
}
