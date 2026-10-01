package com.shop.order.internal.checkout.configuration;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.order.checkout.pricing")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutPricingProperties {

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    BigDecimal discountRate = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    BigDecimal taxRate = BigDecimal.ZERO;
}
