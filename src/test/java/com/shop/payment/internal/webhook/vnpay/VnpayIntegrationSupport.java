package com.shop.payment.internal.webhook.vnpay;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "app.payment.provider.type=vnpay",
            "app.payment.vnpay.tmn-code=TEST1234",
            "app.payment.vnpay.hash-secret=sandbox-test-signature-material-0123456789",
            "app.payment.vnpay.public-base-url=https://api.example.com",
            "app.payment.vnpay.allowed-hosts=api.example.com"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@RecordApplicationEvents
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class VnpayIntegrationSupport {
    static final String SECRET = "sandbox-test-signature-material-0123456789";
    static final String IPN = "/api/payments/webhooks/vnpay";

    @Autowired
    MockMvc mvc;

    @Autowired
    PaymentAttemptRepository attempts;

    @Autowired
    PaymentWebhookEventRepository inbox;

    @Autowired
    VnpayIpnService ipnService;

    PaymentAttempt seed() {
        return seed(Instant.now().minusSeconds(20));
    }

    PaymentAttempt seed(Instant createdAt) {
        UUID id = UUID.randomUUID();
        var attempt =
                PaymentAttempt.start(id, UUID.randomUUID(), 1, new BigDecimal("199000.00"), "VND", "VNPAY", createdAt);
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION,
                VnpayPaymentProvider.reference(id),
                URI.create("https://api.example.com/api/payments/checkout/vnpay/start"),
                null,
                createdAt.plusSeconds(1));
        return attempts.saveAndFlush(attempt);
    }

    Map<String, String> callback(PaymentAttempt attempt) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("vnp_TmnCode", "TEST1234");
        fields.put("vnp_TxnRef", attempt.getProviderReference());
        fields.put("vnp_TransactionNo", "123456789");
        fields.put("vnp_Amount", "19900000");
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + attempt.getOrderId());
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        return fields;
    }

    // Independent fixture implementation: production signer is not used for callback tests.
    static Map<String, String> signed(Map<String, String> fields) {
        Map<String, String> result = new TreeMap<>(fields);
        result.remove("vnp_SecureHash");
        String text = result.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        result.put("vnp_SecureHash", hmac(text));
        return result;
    }

    static String hmac(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
