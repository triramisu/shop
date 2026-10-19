package com.shop.payment.internal.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.provider")
public class PaymentProviderSelectionProperties {

    private PaymentProviderType type = PaymentProviderType.NONE;
}
