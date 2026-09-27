package com.shop.identity.internal.configuration;

import com.shop.identity.internal.constant.IdentityApiPaths;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationRateLimitProperties {

    boolean enabled = true;
    int maxBuckets = 10_000;
    Duration bucketIdleTime = Duration.ofMinutes(10);
    Limit registration = new Limit(5, Duration.ofMinutes(1));
    Limit login = new Limit(10, Duration.ofMinutes(1));
    Limit captcha = new Limit(10, Duration.ofMinutes(1));
    Limit introspection = new Limit(60, Duration.ofMinutes(1));
    Limit refresh = new Limit(30, Duration.ofMinutes(1));
    Limit logout = new Limit(30, Duration.ofMinutes(1));

    @PostConstruct
    void validate() {
        if (maxBuckets <= 0) {
            throw new IllegalStateException("Rate-limit max-buckets must be greater than zero");
        }
        if (bucketIdleTime == null || bucketIdleTime.isZero() || bucketIdleTime.isNegative()) {
            throw new IllegalStateException("Rate-limit bucket-idle-time must be positive");
        }
        List.of(registration, login, captcha, introspection, refresh, logout).forEach(Limit::validate);
    }

    public Limit limitFor(String path) {
        return switch (path) {
            case IdentityApiPaths.REGISTER -> registration;
            case IdentityApiPaths.TOKEN -> login;
            case IdentityApiPaths.CAPTCHA -> captcha;
            case IdentityApiPaths.INTROSPECT -> introspection;
            case IdentityApiPaths.REFRESH -> refresh;
            case IdentityApiPaths.LOGOUT -> logout;
            default -> null;
        };
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Limit {

        long capacity;
        Duration refillPeriod;

        void validate() {
            if (capacity <= 0) {
                throw new IllegalStateException("Rate-limit capacity must be greater than zero");
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalStateException("Rate-limit refill-period must be positive");
            }
        }
    }
}
