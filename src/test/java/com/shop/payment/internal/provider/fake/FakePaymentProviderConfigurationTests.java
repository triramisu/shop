package com.shop.payment.internal.provider.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.provider.PaymentProviderOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FakePaymentProviderConfigurationTests {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(FakePaymentProviderConfiguration.class);

    @Test
    void registersFakeProviderOnlyWhenExplicitlySelected() {
        contextRunner.withPropertyValues("app.payment.provider.type=fake").run(context -> {
            assertThat(context).hasSingleBean(PaymentProviderOperations.class);
            assertThat(context).hasSingleBean(FakePaymentCallbackSimulator.class);
        });

        contextRunner.withPropertyValues("app.payment.provider.type=none").run(context -> {
            assertThat(context).doesNotHaveBean(PaymentProviderOperations.class);
            assertThat(context).doesNotHaveBean(FakePaymentCallbackSimulator.class);
        });
    }
}
