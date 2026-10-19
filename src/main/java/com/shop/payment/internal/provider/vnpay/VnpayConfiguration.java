package com.shop.payment.internal.provider.vnpay;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "vnpay")
@EnableConfigurationProperties(VnpayProperties.class)
class VnpayConfiguration {
    @Bean
    VnpaySignature vnpaySignature(VnpayProperties properties) {
        validate(properties);
        return new VnpaySignature(properties.getHashSecret(), properties.getPreviousHashSecrets());
    }

    @Bean
    VnpayPaymentProvider vnpayPaymentProvider(VnpayProperties properties, VnpaySignature signature) {
        return new VnpayPaymentProvider(properties, signature);
    }

    static void validate(VnpayProperties p) {
        URI base = p.getPublicBaseUrl();
        boolean valid = p.getTmnCode() != null
                && p.getTmnCode().matches("[A-Za-z0-9]{8}")
                && p.getHashSecret() != null
                && p.getHashSecret().matches("[!-~]{32,512}")
                && p.getPreviousHashSecrets() != null
                && p.getPreviousHashSecrets().size() <= 2
                && p.getPreviousHashSecrets().stream().allMatch(key -> key != null && key.matches("[!-~]{32,512}"))
                && base != null
                && "https".equals(base.getScheme())
                && base.getHost() != null
                && (base.getPort() == -1 || base.getPort() == 443)
                && base.getUserInfo() == null
                && base.getRawQuery() == null
                && base.getFragment() == null
                && (base.getPath() == null || base.getPath().isEmpty())
                && p.getAllowedHosts() != null
                && p.getAllowedHosts().contains(base.getHost())
                && p.getCheckoutTtl() != null
                && p.getCheckoutTtl().compareTo(Duration.ofMinutes(1)) >= 0
                && p.getCheckoutTtl().compareTo(Duration.ofMinutes(10)) <= 0
                && p.getBankCode() != null
                && p.getBankCode().matches("[A-Z0-9]{0,20}");
        if (!valid || (base + VnpayPaymentProvider.CHECKOUT_PATH + "/return").length() > 255) {
            throw new IllegalStateException(
                    "Cấu hình VNPay sandbox không hợp lệ; kiểm tra credential merchant, URL HTTPS, host allowlist và TTL");
        }
    }
}
