package com.shop.payment.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentInitiationCommandTests {

    @Test
    void normalizesImmutablePaymentTerms() {
        PaymentInitiationCommand command = new PaymentInitiationCommand(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("125000.50"), " vnd ");

        assertThat(command.amount()).isEqualByComparingTo("125000.50");
        assertThat(command.currency()).isEqualTo("VND");
    }

    @Test
    void rejectsInvalidPaymentTerms() {
        UUID paymentAttemptId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> new PaymentInitiationCommand(paymentAttemptId, orderId, 0, new BigDecimal("1.00"), "VND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt number");
        assertThatThrownBy(() ->
                        new PaymentInitiationCommand(paymentAttemptId, orderId, 1, new BigDecimal("1.001"), "VND"))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new PaymentInitiationCommand(paymentAttemptId, orderId, 1, BigDecimal.ZERO, "VND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
        assertThatThrownBy(
                        () -> new PaymentInitiationCommand(paymentAttemptId, orderId, 1, new BigDecimal("1.00"), "VN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }
}
