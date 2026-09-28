package com.shop.identity.internal.administration.configuration;

import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.identity.admin")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminBootstrapProperties {

    boolean bootstrapEnabled;
    String username;
    String email;

    @ToString.Exclude
    String password;
}
