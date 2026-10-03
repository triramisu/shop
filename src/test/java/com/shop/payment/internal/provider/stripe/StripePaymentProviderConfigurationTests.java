package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.provider.PaymentProviderOperations;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class StripePaymentProviderConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(StripePaymentProviderConfiguration.class);

    @Test
    void registersStripeProviderOnlyWhenExplicitlySelectedAndConfigurationIsValid() {
        contextRunner.withPropertyValues(validProperties()).run(context -> {
            assertThat(context).hasSingleBean(PaymentProviderOperations.class);
            assertThat(context).hasSingleBean(StripeCheckoutGateway.class);
            assertThat(context.getBeansOfType(MeterBinder.class)).hasSize(2);
        });

        contextRunner.withPropertyValues("app.payment.provider.type=none").run(context -> assertThat(context)
                .doesNotHaveBean(PaymentProviderOperations.class));
    }

    @Test
    void failsFastWhenStripeSecretIsMissing() {
        String[] values = validProperties();
        values[1] = "app.payment.stripe.secret-key=";

        contextRunner.withPropertyValues(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage(
                            "Khóa bí mật Stripe phải có từ 32 đến 512 ký tự hiển thị và không chứa khoảng trắng");
        });
    }

    private String[] validProperties() {
        return new String[] {
            "app.payment.provider.type=stripe",
            "app.payment.stripe.secret-key=" + StripeTestCredentials.apiKey(),
            "app.payment.stripe.return-state-secret=" + StripeTestCredentials.stateSigningKey(),
            "app.payment.stripe.success-url=https://shop.example.com/api/payments/checkout/return",
            "app.payment.stripe.cancel-url=https://shop.example.com/api/payments/checkout/cancel",
            "app.payment.stripe.allowed-return-hosts=shop.example.com"
        };
    }
}
