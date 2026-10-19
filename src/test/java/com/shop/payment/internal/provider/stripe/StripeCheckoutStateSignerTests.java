package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StripeCheckoutStateSignerTests {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void issuesAndVerifiesSignedBoundedState() {
        UUID paymentAttemptId = UUID.randomUUID();
        StripeCheckoutStateSigner signer = signerAt(NOW);

        String state = signer.issue(paymentAttemptId);

        assertThat(signer.verify(state)).isEqualTo(paymentAttemptId);
        assertThat(state).doesNotContain(StripeTestCredentials.stateSigningKey());
    }

    @Test
    void rejectsTamperedAndExpiredState() {
        UUID paymentAttemptId = UUID.randomUUID();
        String state = signerAt(NOW).issue(paymentAttemptId);

        assertThatThrownBy(() -> signerAt(NOW).verify(state + "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signerAt(NOW.plus(Duration.ofHours(2))).verify(state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Trạng thái checkout đã hết hạn");
    }

    private StripeCheckoutStateSigner signerAt(Instant instant) {
        return new StripeCheckoutStateSigner(
                StripeTestCredentials.stateSigningKey(), Duration.ofHours(1), Clock.fixed(instant, ZoneOffset.UTC));
    }
}
