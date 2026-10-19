package com.shop.payment.internal.service;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.processing.PaymentAttemptSnapshot;
import com.shop.payment.processing.PaymentInitiationCommand;
import com.shop.payment.provider.PaymentProviderException;
import com.shop.payment.provider.PaymentProviderResult;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class PaymentAttemptTransactionService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentLifecycleEventPublisher lifecycleEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    PaymentAttemptSnapshot getOrCreate(PaymentInitiationCommand command, String providerCode, Instant now) {
        PaymentAttempt byId =
                paymentAttemptRepository.findById(command.paymentAttemptId()).orElse(null);
        if (byId != null) {
            validateSameAttempt(byId, command, providerCode);
            return PaymentAttemptSnapshotMapper.toSnapshot(byId);
        }

        PaymentAttempt byOrderAttempt = paymentAttemptRepository
                .findByOrderIdAndAttemptNumber(command.orderId(), command.attemptNumber())
                .orElse(null);
        if (byOrderAttempt != null) {
            validateSameAttempt(byOrderAttempt, command, providerCode);
            return PaymentAttemptSnapshotMapper.toSnapshot(byOrderAttempt);
        }

        PaymentAttempt created = PaymentAttempt.start(
                command.paymentAttemptId(),
                command.orderId(),
                command.attemptNumber(),
                command.amount(),
                command.currency(),
                providerCode,
                now);
        return PaymentAttemptSnapshotMapper.toSnapshot(paymentAttemptRepository.saveAndFlush(created));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    PaymentAttemptSnapshot applyProviderResult(
            java.util.UUID paymentAttemptId, PaymentProviderResult result, Instant occurredAt) {
        PaymentAttempt attempt = requireAttempt(paymentAttemptId);
        PaymentStatus targetStatus =
                switch (result.status()) {
                    case PENDING -> PaymentStatus.PENDING;
                    case REQUIRES_ACTION -> PaymentStatus.REQUIRES_ACTION;
                    case UNKNOWN -> PaymentStatus.UNKNOWN;
                    case SUCCEEDED -> PaymentStatus.SUCCEEDED;
                    case FAILED -> PaymentStatus.FAILED;
                };
        if (attempt.getStatus() == targetStatus) {
            return PaymentAttemptSnapshotMapper.toSnapshot(attempt);
        }
        var event = attempt.transition(
                targetStatus, result.providerReference(), result.actionUrl(), result.failureCode(), occurredAt);
        PaymentAttemptSnapshot snapshot =
                PaymentAttemptSnapshotMapper.toSnapshot(paymentAttemptRepository.saveAndFlush(attempt));
        lifecycleEventPublisher.publish(event);
        return snapshot;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    PaymentAttemptSnapshot applyProviderError(
            java.util.UUID paymentAttemptId, PaymentProviderException exception, Instant occurredAt) {
        PaymentAttempt attempt = requireAttempt(paymentAttemptId);
        PaymentStatus targetStatus = exception.isRetryable() ? PaymentStatus.UNKNOWN : PaymentStatus.FAILED;
        if (attempt.getStatus() == targetStatus) {
            return PaymentAttemptSnapshotMapper.toSnapshot(attempt);
        }
        String failureCode = targetStatus == PaymentStatus.FAILED ? exception.getProviderErrorCode() : null;
        var event = attempt.transition(targetStatus, null, failureCode, occurredAt);
        PaymentAttemptSnapshot snapshot =
                PaymentAttemptSnapshotMapper.toSnapshot(paymentAttemptRepository.saveAndFlush(attempt));
        lifecycleEventPublisher.publish(event);
        return snapshot;
    }

    private PaymentAttempt requireAttempt(java.util.UUID paymentAttemptId) {
        return paymentAttemptRepository
                .findById(paymentAttemptId)
                .orElseThrow(() -> new IllegalStateException("payment attempt does not exist"));
    }

    private void validateSameAttempt(PaymentAttempt attempt, PaymentInitiationCommand command, String providerCode) {
        if (!attempt.getId().equals(command.paymentAttemptId())
                || !attempt.getOrderId().equals(command.orderId())
                || attempt.getAttemptNumber() != command.attemptNumber()
                || attempt.getAmount().compareTo(command.amount()) != 0
                || !attempt.getCurrency().equals(command.currency())
                || !attempt.getProviderCode().equals(providerCode)) {
            throw new IllegalStateException("payment attempt identity or immutable terms conflict");
        }
    }
}
