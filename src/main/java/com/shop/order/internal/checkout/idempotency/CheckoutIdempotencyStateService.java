package com.shop.order.internal.checkout.idempotency;

import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import com.shop.order.internal.repository.CheckoutIdempotencyRepository;
import com.shop.order.internal.repository.OrderInventoryOrchestrationRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class CheckoutIdempotencyStateService {

    CheckoutIdempotencyRepository idempotencyRepository;
    OrderInventoryOrchestrationRepository orchestrationRepository;
    CheckoutIdempotencyProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    CheckoutIdempotencyClaim claim(String ownerSubject, String keyHash, String requestFingerprint) {
        Instant now = Instant.now();
        return idempotencyRepository
                .findByOwnerSubjectAndOperationAndIdempotencyKeyHash(
                        ownerSubject, CheckoutIdempotencyOperation.CREATE_ORDER, keyHash)
                .map(record -> resolve(record, requestFingerprint, now))
                .orElseGet(() -> create(ownerSubject, keyHash, requestFingerprint, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    CheckoutIdempotencyClaim resolveConcurrentClaim(String ownerSubject, String keyHash, String requestFingerprint) {
        Instant now = Instant.now();
        CheckoutIdempotencyRecord record = idempotencyRepository
                .findByOwnerSubjectAndOperationAndIdempotencyKeyHash(
                        ownerSubject, CheckoutIdempotencyOperation.CREATE_ORDER, keyHash)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_REPLAY_UNAVAILABLE));
        return resolve(record, requestFingerprint, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void complete(UUID executionId, UUID orderId) {
        CheckoutIdempotencyRecord record = loadExecution(executionId);
        record.complete(executionId, orderId, Instant.now());
        idempotencyRepository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fail(UUID executionId, ErrorCode errorCode) {
        CheckoutIdempotencyRecord record = loadExecution(executionId);
        record.fail(executionId, errorCode, Instant.now());
        idempotencyRepository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int purgeExpiredTerminal(Instant expiredBefore) {
        return idempotencyRepository.purgeTerminalExpiredBefore(expiredBefore, CheckoutIdempotencyStatus.PROCESSING);
    }

    private CheckoutIdempotencyClaim create(
            String ownerSubject, String keyHash, String requestFingerprint, Instant now) {
        CheckoutIdempotencyRecord record = CheckoutIdempotencyRecord.start(
                ownerSubject,
                CheckoutIdempotencyOperation.CREATE_ORDER,
                keyHash,
                requestFingerprint,
                now.plus(properties.getRetention()),
                now);
        try {
            idempotencyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException | TransientDataAccessException exception) {
            throw new CheckoutIdempotencyRaceException(exception);
        }
        return CheckoutIdempotencyClaim.proceed(record.getExecutionId());
    }

    private CheckoutIdempotencyClaim resolve(CheckoutIdempotencyRecord record, String requestFingerprint, Instant now) {
        if (record.isExpired(now) && record.getStatus() != CheckoutIdempotencyStatus.PROCESSING) {
            record.restart(requestFingerprint, now.plus(properties.getRetention()), now);
            idempotencyRepository.saveAndFlush(record);
            return CheckoutIdempotencyClaim.proceed(record.getExecutionId());
        }
        if (!record.hasFingerprint(requestFingerprint)) {
            throw new AppException(ErrorCode.CHECKOUT_IDEMPOTENCY_CONFLICT);
        }
        return switch (record.getStatus()) {
            case COMPLETED -> completed(record);
            case FAILED -> CheckoutIdempotencyClaim.failed(resolveFailure(record.getFailureCode()));
            case PROCESSING -> recoverOrReportInProgress(record, now);
        };
    }

    private CheckoutIdempotencyClaim completed(CheckoutIdempotencyRecord record) {
        if (record.getResultOrderId() == null) {
            throw new AppException(ErrorCode.CHECKOUT_REPLAY_UNAVAILABLE);
        }
        return CheckoutIdempotencyClaim.replay(record.getResultOrderId());
    }

    private CheckoutIdempotencyClaim recoverOrReportInProgress(CheckoutIdempotencyRecord record, Instant occurredAt) {
        return orchestrationRepository
                .findByCorrelationId(record.getExecutionId())
                .map(orchestration -> recover(record, orchestration, occurredAt))
                .orElseGet(CheckoutIdempotencyClaim::inProgress);
    }

    private CheckoutIdempotencyClaim recover(
            CheckoutIdempotencyRecord record, OrderInventoryOrchestration orchestration, Instant occurredAt) {
        UUID orderId = orchestration.getOrder().getId();
        record.complete(record.getExecutionId(), orderId, occurredAt);
        idempotencyRepository.saveAndFlush(record);
        return CheckoutIdempotencyClaim.replay(orderId);
    }

    private CheckoutIdempotencyRecord loadExecution(UUID executionId) {
        return idempotencyRepository
                .findByExecutionId(executionId)
                .orElseThrow(() -> new AppException(ErrorCode.CHECKOUT_REPLAY_UNAVAILABLE));
    }

    private ErrorCode resolveFailure(String failureCode) {
        try {
            return ErrorCode.valueOf(failureCode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ErrorCode.UNCATEGORIZED_EXCEPTION;
        }
    }
}
