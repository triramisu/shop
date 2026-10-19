package com.shop.payment.internal.service;

import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.processing.PaymentAttemptQueryOperations;
import com.shop.payment.processing.PaymentAttemptSnapshot;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PaymentAttemptQueryService implements PaymentAttemptQueryOperations {
    private final PaymentAttemptRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttemptSnapshot> findLatestForOrder(UUID orderId) {
        return repository
                .findFirstByOrderIdOrderByAttemptNumberDesc(orderId)
                .map(PaymentAttemptSnapshotMapper::toSnapshot);
    }
}
