package com.shop.payment.internal.provider.fake;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "fake")
@EnableConfigurationProperties(FakePaymentProviderProperties.class)
class FakePaymentProviderConfiguration {

    @Bean
    FakePaymentProviderControls fakePaymentProviderControls(FakePaymentProviderProperties properties) {
        return new FakePaymentProviderControls(properties);
    }

    @Bean
    FakePaymentProvider fakePaymentProvider(
            FakePaymentProviderProperties properties, FakePaymentProviderControls controls) {
        return new FakePaymentProvider(properties, controls);
    }

    @Bean
    FakePaymentCallbackSimulator fakePaymentCallbackSimulator(
            FakePaymentProvider provider, FakePaymentProviderProperties properties) {
        return new FakePaymentCallbackSimulator(provider, properties);
    }
}
