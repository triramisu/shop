package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "app.inventory.reservation.expiration.enabled=true",
            "app.inventory.reservation.expiration.fixed-delay=1h",
            "app.inventory.reservation.expiration.initial-delay=1h"
        })
@ActiveProfiles("test")
class StockReservationExpirationSchedulingIntegrationTests {

    @Autowired
    private StockReservationExpirationJob expirationJob;

    @Test
    void createsScheduledJobWithDurationProperties() {
        assertThat(expirationJob).isNotNull();
    }
}
