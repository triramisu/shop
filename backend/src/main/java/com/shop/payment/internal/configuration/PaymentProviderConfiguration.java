package com.shop.payment.internal.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProviderSelectionProperties.class)
class PaymentProviderConfiguration {

    @Bean
    PaymentProviderProductionGuard paymentProviderProductionGuard(
            PaymentProviderSelectionProperties properties, Environment environment) {
        return new PaymentProviderProductionGuard(properties, environment);
    }
}
