package com.shop.inventory.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.inventory.internal.configuration.InventoryReservationProperties;
import com.shop.inventory.internal.repository.StockReservationRepository;
import com.shop.inventory.reservation.StockReservationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StockReservationExpirationProcessorTests {

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private StockReservationService reservationService;

    private InventoryReservationProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private StockReservationExpirationProcessor processor;

    @BeforeEach
    void setUp() {
        properties = new InventoryReservationProperties();
        properties.getExpiration().setBatchSize(3);
        meterRegistry = new SimpleMeterRegistry();
        processor = new StockReservationExpirationProcessor(
                stockReservationRepository, reservationService, properties, meterRegistry);
    }

    @Test
    void continuesBatchWhenOneReservationFails() {
        Instant cutoff = Instant.parse("2026-10-01T03:00:00Z");
        UUID expiredId = UUID.randomUUID();
        UUID skippedId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        when(stockReservationRepository.findIdsByStatusAndExpiration(
                        eq(StockReservationStatus.RESERVED), eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(expiredId, skippedId, failedId));
        when(reservationService.expireIfDue(expiredId, cutoff)).thenReturn(true);
        when(reservationService.expireIfDue(skippedId, cutoff)).thenReturn(false);
        when(reservationService.expireIfDue(failedId, cutoff)).thenThrow(new IllegalStateException("database down"));

        var result = processor.expireBatch(cutoff);

        assertThat(result).isEqualTo(new StockReservationExpirationProcessor.ExpirationBatchResult(3, 1, 1, 1));
        verify(reservationService).expireIfDue(expiredId, cutoff);
        verify(reservationService).expireIfDue(skippedId, cutoff);
        verify(reservationService).expireIfDue(failedId, cutoff);
        assertThat(meterRegistry
                        .get(StockReservationExpirationProcessor.ITEM_METRIC)
                        .tag("outcome", "expired")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get(StockReservationExpirationProcessor.ITEM_METRIC)
                        .tag("outcome", "skipped")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get(StockReservationExpirationProcessor.ITEM_METRIC)
                        .tag("outcome", "failed")
                        .counter()
                        .count())
                .isEqualTo(1);
    }
}
