package com.shop.order.internal.checkout.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CheckoutPricingProperties.class)
public class CheckoutPricingConfiguration {}
