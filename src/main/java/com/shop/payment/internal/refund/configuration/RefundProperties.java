package com.shop.payment.internal.refund.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.payment.refund")
public class RefundProperties {
    private boolean enabled;
}
