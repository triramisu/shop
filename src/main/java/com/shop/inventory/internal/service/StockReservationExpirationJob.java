package com.shop.inventory.internal.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "app.inventory.reservation.expiration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class StockReservationExpirationJob {

    static final String RUN_METRIC = "shop.inventory.reservation.expiration.runs";

    StockReservationExpirationProcessor processor;
    MeterRegistry meterRegistry;

    @Scheduled(
            fixedDelayString = "${app.inventory.reservation.expiration.fixed-delay:30s}",
            initialDelayString = "${app.inventory.reservation.expiration.initial-delay:30s}")
    void expireDueReservations() {
        try {
            var result = processor.expireBatch(Instant.now());
            meterRegistry.counter(RUN_METRIC, "outcome", "success").increment();
            if (result.selected() > 0) {
                log.info(
                        "Processed stock reservation expiration batch: selected={}, expired={}, skipped={}, failed={}",
                        result.selected(),
                        result.expired(),
                        result.skipped(),
                        result.failed());
            }
        } catch (RuntimeException exception) {
            meterRegistry.counter(RUN_METRIC, "outcome", "failed").increment();
            log.error("Stock reservation expiration batch failed", exception);
        }
    }
}
