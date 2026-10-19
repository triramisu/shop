package com.shop.payment.internal.provider.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderRequest;
import com.shop.payment.provider.PaymentProviderStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VnpayProviderTests {
    private static final String SECRET = "sandbox-test-signature-material-0123456789";
    private final VnpayProperties properties = properties();
    private final VnpaySignature signer = new VnpaySignature(SECRET);
    private final VnpayPaymentProvider provider = new VnpayPaymentProvider(properties, signer);

    @Test
    void rotatedSecretSignsNewRequestsButAcceptsOldCallbacksAndHandoffDuringGraceWindow() {
        var current = new VnpaySignature("new-sandbox-signature-key-0123456789012345", List.of(SECRET));
        var fields = new java.util.HashMap<>(Map.of("vnp_Amount", "100"));
        fields.put("vnp_SecureHash", signer.sign(fields));
        assertThat(current.verify(fields)).isTrue();
        assertThat(current.sign(fields)).isNotEqualTo(signer.sign(fields));
        UUID id = UUID.randomUUID();
        assertThat(current.verifyCheckoutToken(id, signer.checkoutToken(id))).isTrue();
    }

    @Test
    void matchesIndependentNodeCryptoGoldenVector() {
        assertThat(signer.sign(Map.of(
                        "vnp_Amount",
                        "19900000",
                        "vnp_OrderInfo",
                        "Thanh toan don hang test",
                        "vnp_TmnCode",
                        "TEST1234")))
                .isEqualTo(
                        "eb0c2463c41ef8b8161512c2d8ca379825967a77733c9c74dc4fe1f5c75239ca6c452cbceec9fc49121cc8be5d10b2a8ab8c0fa7030a3defb7cb1367626560a0");
    }

    @Test
    void canonicalizesEncodedSortedNonEmptyParametersWithoutHashFields() {
        var fields = Map.of("vnp_Z", "a b+c:/", "vnp_A", "100", "vnp_Empty", "", "vnp_SecureHash", "ignored");
        assertThat(signer.canonical(fields)).isEqualTo("vnp_A=100&vnp_Z=a+b%2Bc%3A%2F");
        var signed = new java.util.HashMap<>(fields);
        signed.put("vnp_SecureHash", signer.sign(fields).toUpperCase());
        assertThat(signer.verify(signed)).isTrue();
        signed.put("vnp_A", "101");
        assertThat(signer.verify(signed)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "ZZZZ", "0123456789"})
    void rejectsMalformedSignatures(String hash) {
        assertThat(signer.verify(Map.of("vnp_SecureHash", hash))).isFalse();
    }

    @Test
    void handoffIsDeterministicBoundToAttemptAndDifferentFromPaymentSignature() {
        UUID id = UUID.randomUUID();
        var request =
                new PaymentProviderRequest(id, UUID.randomUUID(), 1, new BigDecimal("199000"), "VND", "key-12345678");
        var result = provider.createPayment(request);
        assertThat(provider.providerCode()).isEqualTo("VNPAY");
        assertThat(result.status()).isEqualTo(PaymentProviderStatus.REQUIRES_ACTION);
        assertThat(result.providerReference()).isEqualTo(VnpayPaymentProvider.reference(id));
        assertThat(result).isEqualTo(provider.createPayment(request));
        assertThat(signer.verifyCheckoutToken(id, signer.checkoutToken(id))).isTrue();
        assertThat(signer.verifyCheckoutToken(UUID.randomUUID(), signer.checkoutToken(id)))
                .isFalse();
        assertThat(signer.verifyCheckoutToken(id, null)).isFalse();
        assertThat(result.actionUrl().toString())
                .startsWith("https://api.example.com/api/payments/checkout/vnpay/start?");
    }

    @Test
    void gatewayUsesVndMinorUnitsFixedSandboxAndVietnamTimeWithOriginalExpiry() {
        UUID id = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        var url = provider.gatewayUrl(
                id, order, new BigDecimal("199000.00"), "VND", Instant.parse("2026-10-19T01:00:00Z"), "203.0.113.10");
        assertThat(url.toString())
                .startsWith(VnpayPaymentProvider.SANDBOX_URL + "?")
                .contains(
                        "vnp_Amount=19900000",
                        "vnp_CreateDate=20261019080000",
                        "vnp_ExpireDate=20261019081000",
                        "vnp_CurrCode=VND",
                        "vnp_IpAddr=203.0.113.10",
                        "vnp_TxnRef=" + VnpayPaymentProvider.reference(id))
                .doesNotContain("vnp_BankCode", SECRET);
        properties.setBankCode("VNPAYQR");
        assertThat(provider.gatewayUrl(id, order, BigDecimal.TEN, "VND", Instant.now(), "127.0.0.1")
                        .toString())
                .contains("vnp_BankCode=VNPAYQR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.01", "10000000000"})
    void rejectsUnsupportedAmountsWithoutRounding(String value) {
        assertThatThrownBy(() -> VnpayPaymentProvider.amount(new BigDecimal(value), "VND"))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void refusesForeignCurrencyAndAcceptsAmountBoundary() {
        assertThatThrownBy(() -> VnpayPaymentProvider.amount(BigDecimal.ONE, "USD"))
                .isInstanceOf(PaymentProviderException.class);
        assertThat(VnpayPaymentProvider.amount(new BigDecimal("9999999999"), "VND"))
                .isEqualTo("999999999900");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://api.example.com",
                "https://api.example.com:8443",
                "https://user@api.example.com",
                "https://evil.example.com",
                "https://api.example.com/",
                "https://api.example.com?q=1",
                "https://api.example.com#x"
            })
    void rejectsUnsafePublicBaseUrl(String value) {
        properties.setPublicBaseUrl(URI.create(value));
        assertThatThrownBy(() -> VnpayConfiguration.validate(properties)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesSecretMerchantTtlBankAndOptInStartup() {
        VnpayConfiguration.validate(properties);
        properties.setHashSecret("short");
        assertThatThrownBy(() -> VnpayConfiguration.validate(properties)).isInstanceOf(IllegalStateException.class);
        properties.setHashSecret(SECRET);
        properties.setTmnCode("invalid");
        assertThatThrownBy(() -> VnpayConfiguration.validate(properties)).isInstanceOf(IllegalStateException.class);
        properties.setTmnCode("TEST1234");
        properties.setCheckoutTtl(Duration.ofSeconds(59));
        assertThatThrownBy(() -> VnpayConfiguration.validate(properties)).isInstanceOf(IllegalStateException.class);
        properties.setCheckoutTtl(Duration.ofMinutes(11));
        assertThatThrownBy(() -> VnpayConfiguration.validate(properties)).isInstanceOf(IllegalStateException.class);
        properties.setCheckoutTtl(Duration.ofMinutes(10));
        properties.setBankCode("bad&code");
        assertThatThrownBy(() -> VnpayConfiguration.validate(properties)).isInstanceOf(IllegalStateException.class);
        new ApplicationContextRunner()
                .withUserConfiguration(VnpayConfiguration.class)
                .withPropertyValues("app.payment.provider.type=none")
                .run(context -> assertThat(context).doesNotHaveBean(VnpayPaymentProvider.class));
        new ApplicationContextRunner()
                .withUserConfiguration(VnpayConfiguration.class)
                .withPropertyValues("app.payment.provider.type=vnpay")
                .run(context -> assertThat(context).hasFailed());
    }

    private static VnpayProperties properties() {
        var p = new VnpayProperties();
        p.setTmnCode("TEST1234");
        p.setHashSecret(SECRET);
        p.setPublicBaseUrl(URI.create("https://api.example.com"));
        p.setAllowedHosts(Set.of("api.example.com"));
        return p;
    }
}
