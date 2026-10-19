package com.shop.payment.internal.provider.vnpay;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.payment.vnpay")
public class VnpayProperties {
    private String tmnCode;
    private String hashSecret;
    private List<String> previousHashSecrets = List.of();
    private URI publicBaseUrl;
    private Set<String> allowedHosts = Set.of();
    private Duration checkoutTtl = Duration.ofMinutes(10);
    private String bankCode = "";
}
