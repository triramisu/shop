package com.shop.identity.internal.administration.configuration;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.identity.super-admin")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SuperAdminBootstrapProperties {

    boolean bootstrapEnabled;
    String username;
    String email;

    @ToString.Exclude
    String password;

    @PostConstruct
    void validate() {
        if (!bootstrapEnabled) {
            return;
        }
        if (username == null || !username.matches("^[A-Za-z0-9._-]{3,50}$")) {
            throw new IllegalStateException("SUPER_ADMIN_USERNAME must be 3-50 valid username characters");
        }
        if (email == null || email.length() > 320 || !email.matches("^[^\\s@]+@[^\\s@]+$")) {
            throw new IllegalStateException("SUPER_ADMIN_EMAIL must be a valid email address");
        }
        if (password == null || password.length() < 12 || password.length() > 64) {
            throw new IllegalStateException("SUPER_ADMIN_PASSWORD must contain 12-64 characters");
        }
    }
}
