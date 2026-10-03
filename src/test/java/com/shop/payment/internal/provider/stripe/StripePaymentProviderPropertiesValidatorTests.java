package com.shop.payment.internal.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StripePaymentProviderPropertiesValidatorTests {

    @Test
    void acceptsSecureBoundedConfigurationAndNormalizesHosts() {
        StripePaymentProviderProperties properties = validProperties();
        properties.setAllowedReturnHosts(new LinkedHashSet<>(Set.of("SHOP.EXAMPLE.COM")));

        assertThatCode(() -> new StripePaymentProviderPropertiesValidator(properties).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThat(properties.getAllowedReturnHosts()).containsExactly("shop.example.com");
    }

    @Test
    void acceptsHttpLoopbackOnlyForStripeTestKeys() {
        StripePaymentProviderProperties testProperties = validProperties();
        testProperties.setSuccessUrl(URI.create("http://localhost:8080/api/payments/checkout/return"));
        testProperties.setCancelUrl(URI.create("http://127.0.0.1:8080/api/payments/checkout/cancel"));
        testProperties.setAllowedReturnHosts(new LinkedHashSet<>(Set.of("localhost", "127.0.0.1")));
        StripePaymentProviderProperties liveProperties = validProperties();
        liveProperties.setSecretKey("sk_live_" + "a".repeat(32));
        liveProperties.setSuccessUrl(URI.create("http://localhost:8080/api/payments/checkout/return"));
        liveProperties.setAllowedReturnHosts(new LinkedHashSet<>(Set.of("localhost")));

        assertThatCode(() -> new StripePaymentProviderPropertiesValidator(testProperties).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(liveProperties).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL Stripe thành công");
    }

    @Test
    void rejectsMissingSecretsInsecureRedirectsAndUnknownHosts() {
        StripePaymentProviderProperties missingSecret = validProperties();
        missingSecret.setSecretKey("");
        StripePaymentProviderProperties insecureRedirect = validProperties();
        insecureRedirect.setSuccessUrl(URI.create("http://shop.example.com/api/payments/checkout/return"));
        StripePaymentProviderProperties unknownHost = validProperties();
        unknownHost.setCancelUrl(URI.create("https://evil.example/api/payments/checkout/cancel"));
        StripePaymentProviderProperties wrongPath = validProperties();
        wrongPath.setSuccessUrl(URI.create("https://shop.example.com/payments/return/success"));

        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(missingSecret).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Khóa bí mật");
        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(insecureRedirect).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL Stripe thành công");
        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(unknownHost).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL Stripe hủy");
        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(wrongPath).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL Stripe thành công");
    }

    @Test
    void rejectsRetryTimeoutAndCircuitBreakerSettingsOutsideSafetyBounds() {
        StripePaymentProviderProperties retry = validProperties();
        retry.getRetry().setMaxAttempts(4);
        StripePaymentProviderProperties timeout = validProperties();
        timeout.setCallTimeout(Duration.ofMillis(100));
        StripePaymentProviderProperties circuitBreaker = validProperties();
        circuitBreaker.getCircuitBreaker().setMinimumNumberOfCalls(21);

        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(retry).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thử lại");
        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(timeout).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("toàn bộ lời gọi");
        assertThatThrownBy(() -> new StripePaymentProviderPropertiesValidator(circuitBreaker).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("circuit breaker");
    }

    private StripePaymentProviderProperties validProperties() {
        StripePaymentProviderProperties properties = new StripePaymentProviderProperties();
        properties.setSecretKey(StripeTestCredentials.apiKey());
        properties.setReturnStateSecret(StripeTestCredentials.stateSigningKey());
        properties.setSuccessUrl(URI.create("https://shop.example.com/api/payments/checkout/return"));
        properties.setCancelUrl(URI.create("https://shop.example.com/api/payments/checkout/cancel"));
        properties.setAllowedReturnHosts(new LinkedHashSet<>(Set.of("shop.example.com")));
        return properties;
    }
}
