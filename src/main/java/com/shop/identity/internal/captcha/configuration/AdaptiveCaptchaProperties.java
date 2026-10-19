package com.shop.identity.internal.captcha.configuration;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security.captcha")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdaptiveCaptchaProperties {

    boolean enabled = true;
    int failureThreshold = 3;
    Duration failureWindow = Duration.ofMinutes(15);
    Duration challengeTtl = Duration.ofMinutes(2);
    int maxActiveChallenges = 10_000;

    @PostConstruct
    void validate() {
        if (failureThreshold <= 0) {
            throw new IllegalStateException("Captcha failure-threshold must be greater than zero");
        }
        if (failureWindow == null || failureWindow.isZero() || failureWindow.isNegative()) {
            throw new IllegalStateException("Captcha failure-window must be positive");
        }
        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalStateException("Captcha challenge-ttl must be positive");
        }
        if (maxActiveChallenges <= 0) {
            throw new IllegalStateException("Captcha max-active-challenges must be greater than zero");
        }
    }
}
