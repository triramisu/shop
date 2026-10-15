package com.shop.payment.internal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.processing.PaymentInitiationCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentInitiationServiceTests {

    @Test
    void rejectsInvocationWhenNoProviderIsConfigured() {
        PaymentInitiationService service = new PaymentInitiationService(List.of(), null);
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("1000.00"), "VND");

        assertThatThrownBy(() -> service.initiate(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Exactly one payment provider");
    }
}
