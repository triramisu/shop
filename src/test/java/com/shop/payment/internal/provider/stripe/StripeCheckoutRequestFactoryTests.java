package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StripeCheckoutRequestFactoryTests {

    private final StripeCheckoutRequestFactory factory = new StripeCheckoutRequestFactory(
            URI.create("https://shop.example.com/api/payments/checkout/return"),
            URI.create("https://shop.example.com/api/payments/checkout/cancel"),
            new StripeCheckoutStateSigner(
                    StripeTestCredentials.stateSigningKey(),
                    Duration.ofHours(1),
                    Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC)));

    @Test
    void createsHostedCheckoutFormUsingServerOwnedOrderTerms() {
        UUID paymentAttemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        StripeCheckoutRequest result = factory.create(new PaymentProviderRequest(
                paymentAttemptId, orderId, 1, new BigDecimal("250000.00"), "VND", "payment-idempotency-key"));
        Map<String, String> fields =
                result.formFields().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(result.amountInMinorUnit()).isEqualTo(250000L);
        assertThat(result.currency()).isEqualTo("vnd");
        assertThat(fields)
                .containsEntry("mode", "payment")
                .containsEntry("ui_mode", "hosted_page")
                .containsEntry("client_reference_id", orderId.toString())
                .containsEntry("metadata[payment_attempt_id]", paymentAttemptId.toString())
                .containsEntry("line_items[0][price_data][unit_amount]", "250000");
        assertThat(fields.get("success_url"))
                .startsWith("https://shop.example.com/api/payments/checkout/return?state=")
                .endsWith("&session_id={CHECKOUT_SESSION_ID}");
        assertThat(fields.get("cancel_url")).startsWith("https://shop.example.com/api/payments/checkout/cancel?state=");
        assertThat(fields.keySet())
                .noneMatch(name -> name.toLowerCase().contains("card")
                        || name.toLowerCase().contains("pan")
                        || name.toLowerCase().contains("cvv")
                        || name.toLowerCase().contains("cvc"));
    }

    @Test
    void convertsTwoDecimalCurrencyAndRejectsFractionalVnd() {
        StripeCheckoutRequest usd = factory.create(new PaymentProviderRequest(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("10.25"), "USD", "payment-idempotency-usd"));

        assertThat(usd.amountInMinorUnit()).isEqualTo(1025L);
        assertThatThrownBy(() -> factory.create(new PaymentProviderRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        new BigDecimal("10.25"),
                        "VND",
                        "payment-idempotency-vnd")))
                .isInstanceOf(PaymentProviderException.class);
    }
}
