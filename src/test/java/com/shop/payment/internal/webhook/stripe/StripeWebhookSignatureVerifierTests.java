package com.shop.payment.internal.webhook.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripeWebhookSignatureVerifierTests {

    private static final Instant NOW = Instant.parse("2026-10-05T01:00:00Z");
    private static final byte[] PAYLOAD = "{\"id\":\"evt_test_001\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsAnExactRawPayloadSignedByAnyActiveRotationSecret() {
        String previousSecret = "whsec_" + "p".repeat(32);
        StripeWebhookSignatureVerifier verifier =
                verifier(List.of(previousSecret, StripeWebhookTestSupport.WEBHOOK_SECRET));
        String header = StripeWebhookTestSupport.signature(PAYLOAD, NOW, StripeWebhookTestSupport.WEBHOOK_SECRET);

        VerifiedStripeSignature result = verifier.verify(PAYLOAD, "v0=ignored," + header);

        assertThat(result.signedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsTamperedMissingMalformedOldAndFutureSignatures() {
        StripeWebhookSignatureVerifier verifier = verifier(List.of(StripeWebhookTestSupport.WEBHOOK_SECRET));
        String validHeader = StripeWebhookTestSupport.signature(PAYLOAD, NOW);

        assertRejected(() -> verifier.verify("{}".getBytes(StandardCharsets.UTF_8), validHeader));
        assertRejected(() -> verifier.verify(PAYLOAD, null));
        assertRejected(() -> verifier.verify(PAYLOAD, "t=invalid,v1=abcd"));
        assertRejected(
                () -> verifier.verify(PAYLOAD, StripeWebhookTestSupport.signature(PAYLOAD, NOW.minusSeconds(301))));
        assertRejected(
                () -> verifier.verify(PAYLOAD, StripeWebhookTestSupport.signature(PAYLOAD, NOW.plusSeconds(301))));
    }

    private StripeWebhookSignatureVerifier verifier(List<String> secrets) {
        return new StripeWebhookSignatureVerifier(secrets, Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID));
    }
}
