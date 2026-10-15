package com.shop.order.internal.payment.service;

import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class OrderPaymentRecoveryStateService {

    OrderInventoryOrchestrationRepository orchestrationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markRequired(UUID orderId) {
        orchestrationRepository.findByOrderIdForUpdate(orderId).ifPresent(orchestration -> {
            orchestration.markPaymentRecoveryRequired(Instant.now().truncatedTo(ChronoUnit.MICROS));
            orchestrationRepository.saveAndFlush(orchestration);
        });
    }
}
