package com.shop.inventory.internal.service;

import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.StockReservationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockReservationExpirationProcessor {

    static final String ITEM_METRIC = "shop.inventory.reservation.expiration.items";

    StockReservationRepository stockReservationRepository;
    StockReservationService reservationService;
    InventoryReservationProperties properties;
    MeterRegistry meterRegistry;

    public ExpirationBatchResult expireBatch(Instant cutoff) {
        Objects.requireNonNull(cutoff, "expiration cutoff is required");
        int batchSize = properties.getExpiration().getBatchSize();
        List<UUID> candidates = stockReservationRepository.findIdsByStatusAndExpiration(
                StockReservationStatus.RESERVED, cutoff, PageRequest.of(0, batchSize));

        int expired = 0;
        int skipped = 0;
        int failed = 0;
        for (UUID reservationId : candidates) {
            try {
                if (reservationService.expireIfDue(reservationId, cutoff)) {
                    expired++;
                    recordItem("expired");
                } else {
                    skipped++;
                    recordItem("skipped");
                }
            } catch (RuntimeException exception) {
                failed++;
                recordItem("failed");
                log.warn(
                        "Could not expire stock reservation: reservationId={}, errorType={}",
                        reservationId,
                        exception.getClass().getSimpleName());
            }
        }
        return new ExpirationBatchResult(candidates.size(), expired, skipped, failed);
    }

    private void recordItem(String outcome) {
        meterRegistry.counter(ITEM_METRIC, "outcome", outcome).increment();
    }

    public record ExpirationBatchResult(int selected, int expired, int skipped, int failed) {

        public ExpirationBatchResult {
            if (selected < 0 || expired < 0 || skipped < 0 || failed < 0 || selected != expired + skipped + failed) {
                throw new IllegalArgumentException("expiration batch result is inconsistent");
            }
        }
    }
}
