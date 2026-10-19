package com.shop.payment.internal.configuration;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class PaymentProviderProductionGuard implements InitializingBean {

    private final PaymentProviderSelectionProperties properties;
    private final Environment environment;

    PaymentProviderProductionGuard(PaymentProviderSelectionProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (properties.getType() == null) {
            throw new IllegalStateException("Payment provider type is required");
        }
        if (properties.getType() == PaymentProviderType.FAKE && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("Fake payment provider must not be enabled in production");
        }
    }
}
