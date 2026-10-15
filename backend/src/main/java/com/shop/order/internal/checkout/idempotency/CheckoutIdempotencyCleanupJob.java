package com.shop.order.internal.checkout.idempotency;

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
        prefix = "app.order.checkout.idempotency.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class CheckoutIdempotencyCleanupJob {

    CheckoutIdempotencyStateService stateService;

    @Scheduled(
            fixedDelayString = "${app.order.checkout.idempotency.cleanup.fixed-delay:1h}",
            initialDelayString = "${app.order.checkout.idempotency.cleanup.initial-delay:1h}")
    void purgeExpiredRecords() {
        try {
            int deleted = stateService.purgeExpiredTerminal(Instant.now());
            if (deleted > 0) {
                log.info("Purged expired checkout idempotency records: deleted={}", deleted);
            }
        } catch (RuntimeException exception) {
            log.error("Checkout idempotency cleanup failed", exception);
        }
    }
}
