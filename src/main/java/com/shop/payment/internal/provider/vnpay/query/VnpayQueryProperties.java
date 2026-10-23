package com.shop.payment.internal.provider.vnpay.query;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.payment.vnpay.query")
public class VnpayQueryProperties {
    private boolean enabled;
    private String serverIp = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Duration callTimeout = Duration.ofSeconds(5);

    static void validate(VnpayQueryProperties properties) {
        if (!validIpv4(properties.serverIp)
                || !bounded(properties.connectTimeout)
                || !bounded(properties.readTimeout)
                || !bounded(properties.callTimeout)
                || properties.callTimeout.compareTo(properties.connectTimeout) < 0
                || properties.callTimeout.compareTo(properties.readTimeout) < 0) {
            throw new IllegalStateException(
                    "VNPay query requires a server IPv4 and bounded connect/read/call timeouts");
        }
    }

    private static boolean bounded(Duration duration) {
        return duration != null
                && duration.compareTo(Duration.ofMillis(100)) >= 0
                && duration.compareTo(Duration.ofSeconds(10)) <= 0;
    }

    private static boolean validIpv4(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) {
            return false;
        }
        for (String octet : value.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }
}
