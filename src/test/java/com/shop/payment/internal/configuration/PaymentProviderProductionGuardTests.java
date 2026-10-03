package com.shop.payment.internal.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PaymentProviderProductionGuardTests {

    @Test
    void defaultsToNoProvider() {
        PaymentProviderSelectionProperties properties = new PaymentProviderSelectionProperties();

        assertThat(properties.getType()).isEqualTo(PaymentProviderType.NONE);
    }

    @Test
    void allowsFakeProviderOutsideProduction() {
        PaymentProviderSelectionProperties properties = new PaymentProviderSelectionProperties();
        properties.setType(PaymentProviderType.FAKE);

        assertThatCode(() -> new PaymentProviderProductionGuard(properties, new MockEnvironment()).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFakeProviderInProduction() {
        PaymentProviderSelectionProperties properties = new PaymentProviderSelectionProperties();
        properties.setType(PaymentProviderType.FAKE);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new PaymentProviderProductionGuard(properties, environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be enabled in production");
    }

    @Test
    void rejectsMissingProviderSelection() {
        PaymentProviderSelectionProperties properties = new PaymentProviderSelectionProperties();
        properties.setType(null);

        assertThatThrownBy(() ->
                        new PaymentProviderProductionGuard(properties, new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type is required");
    }
}
