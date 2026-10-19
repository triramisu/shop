package com.shop.order.internal.payment.service;

import com.shop.order.internal.payment.entity.OrderPaymentEventOutcome;
import com.shop.payment.event.PaymentStatusChangedEvent;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderPaymentEventCoordinator {

    static final String EVENT_METRIC = "shop.order.payment.events";
    static final String FAILURE_MANUAL_ACTION = "PAYMENT_MANUAL_ACTION_REQUIRED";
    static final String FAILURE_RETRY_REQUIRED = "PAYMENT_EFFECT_RETRY_REQUIRED";

    OrderPaymentEventInboxService inboxService;
    OrderPaymentOutcomeService outcomeService;
    OrderPaymentRecoveryStateService recoveryStateService;
    MeterRegistry meterRegistry;

    public void handle(PaymentStatusChangedEvent event) {
        Instant receivedAt = now();
        OrderPaymentEventReceipt receipt;
        try {
            receipt = inboxService.receive(event, receivedAt);
        } catch (DataAccessException exception) {
            receipt = inboxService.resolveConcurrentDuplicate(event).orElseThrow(() -> exception);
        }
        if (!receipt.processable()) {
            record("duplicate");
            return;
        }
        process(event.eventId());
    }

    public void recover(UUID eventId) {
        process(eventId);
    }

    private void process(UUID eventId) {
        var claimed = inboxService.claim(eventId);
        if (claimed.isEmpty()) {
            record("skipped");
            return;
        }
        PaymentStatusChangedEvent event = claimed.get();
        try {
            OrderPaymentApplyResult result = outcomeService.apply(event);
            OrderPaymentEventOutcome outcome = result == OrderPaymentApplyResult.COMPLETED
                    ? OrderPaymentEventOutcome.COMPLETED
                    : OrderPaymentEventOutcome.IGNORED;
            inboxService.complete(eventId, outcome, null, now());
            record(outcome.name().toLowerCase(java.util.Locale.ROOT));
        } catch (OrderPaymentManualActionException exception) {
            completeFailure(event, OrderPaymentEventOutcome.MANUAL_ACTION_REQUIRED, FAILURE_MANUAL_ACTION, exception);
        } catch (AppException exception) {
            if (isRetryable(exception.getErrorCode())) {
                completeFailure(event, OrderPaymentEventOutcome.RETRY_REQUIRED, FAILURE_RETRY_REQUIRED, exception);
            } else {
                completeFailure(
                        event, OrderPaymentEventOutcome.MANUAL_ACTION_REQUIRED, FAILURE_MANUAL_ACTION, exception);
            }
        } catch (RuntimeException exception) {
            completeFailure(event, OrderPaymentEventOutcome.RETRY_REQUIRED, FAILURE_RETRY_REQUIRED, exception);
        }
    }

    private void completeFailure(
            PaymentStatusChangedEvent event,
            OrderPaymentEventOutcome outcome,
            String failureCode,
            RuntimeException exception) {
        try {
            recoveryStateService.markRequired(event.orderId());
        } catch (RuntimeException recoveryException) {
            exception.addSuppressed(recoveryException);
        }
        inboxService.complete(event.eventId(), outcome, failureCode, now());
        record(outcome.name().toLowerCase(java.util.Locale.ROOT));
        log.error(
                "Order payment event processing failed: eventId={}, paymentAttemptId={}, orderId={}, outcome={}",
                event.eventId(),
                event.paymentAttemptId(),
                event.orderId(),
                outcome,
                exception);
    }

    private boolean isRetryable(ErrorCode errorCode) {
        return errorCode == ErrorCode.INVENTORY_RESERVATION_UNAVAILABLE
                || errorCode == ErrorCode.STOCK_RESERVATION_REPLAY_UNAVAILABLE;
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private void record(String outcome) {
        meterRegistry.counter(EVENT_METRIC, "outcome", outcome).increment();
    }
}
