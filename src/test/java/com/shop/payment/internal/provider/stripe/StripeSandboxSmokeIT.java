package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.processing.PaymentAttemptSnapshot;
import com.shop.payment.processing.PaymentInitiationCommand;
import com.shop.payment.processing.PaymentInitiationOperations;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "app.payment.provider.type=stripe",
            "app.order.checkout.inventory.reconciliation.enabled=false",
            "app.inventory.reservation.expiration.enabled=false"
        })
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "stripe.sandbox.enabled", matches = "true")
class StripeSandboxSmokeIT {

    private static final String STRIPE_TEST_KEY_PREFIX = "sk_test_";

    @Autowired
    private PaymentInitiationOperations paymentInitiationOperations;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Value("${app.payment.stripe.secret-key}")
    private String stripeSecretKey;

    @Test
    void createsAndPersistsRealHostedCheckoutSessionWithoutReinvokingStripeOnReplay() {
        requireSandboxCredential();
        PaymentInitiationCommand command = new PaymentInitiationCommand(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("199000.00"), "VND");

        PaymentAttemptSnapshot created = paymentInitiationOperations.initiate(command);
        PaymentAttemptSnapshot replay = paymentInitiationOperations.initiate(command);

        assertThat(created.status()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(created.providerCode()).isEqualTo("STRIPE");
        assertThat(created.providerReference()).startsWith("cs_test_");
        assertSafeCheckoutUrl(created.actionUrl());
        assertThat(created.failureCode()).isNull();
        assertThat(created.completedAt()).isNull();
        assertThat(replay).isEqualTo(created);
        assertPersistedAttempt(created);
    }

    private void requireSandboxCredential() {
        if (stripeSecretKey == null || !stripeSecretKey.startsWith(STRIPE_TEST_KEY_PREFIX)) {
            throw new IllegalStateException("Smoke test Stripe chỉ được phép chạy bằng khóa sk_test_");
        }
    }

    private void assertSafeCheckoutUrl(URI actionUrl) {
        assertThat(actionUrl).isNotNull();
        assertThat(actionUrl.getScheme()).isEqualTo("https");
        assertThat(actionUrl.getHost()).isEqualTo("checkout.stripe.com");
        assertThat(actionUrl.getUserInfo()).isNull();
        assertThat(actionUrl.getPort()).isIn(-1, 443);
    }

    private void assertPersistedAttempt(PaymentAttemptSnapshot snapshot) {
        PaymentAttempt persisted = paymentAttemptRepository
                .findById(snapshot.id())
                .orElseThrow(() -> new AssertionError("Không tìm thấy payment attempt sau smoke test Stripe"));

        assertThat(persisted.getOrderId()).isEqualTo(snapshot.orderId());
        assertThat(persisted.getAttemptNumber()).isEqualTo(1);
        assertThat(persisted.getAmount()).isEqualByComparingTo("199000.00");
        assertThat(persisted.getCurrency()).isEqualTo("VND");
        assertThat(persisted.getProviderCode()).isEqualTo("STRIPE");
        assertThat(persisted.getProviderReference()).isEqualTo(snapshot.providerReference());
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(persisted.getActionUrl()).isEqualTo(snapshot.actionUrl());
        assertThat(persisted.getFailureCode()).isNull();
        assertThat(persisted.getCompletedAt()).isNull();
    }
}
