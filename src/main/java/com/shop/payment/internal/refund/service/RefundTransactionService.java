package com.shop.payment.internal.refund.service;

import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.refund.constant.RefundConstants;
import com.shop.payment.internal.refund.dto.RefundResponse;
import com.shop.payment.internal.refund.entity.RefundAttempt;
import com.shop.payment.internal.refund.entity.RefundBudget;
import com.shop.payment.internal.refund.entity.RefundStatus;
import com.shop.payment.internal.refund.mapper.RefundMapper;
import com.shop.payment.internal.refund.repository.RefundAttemptRepository;
import com.shop.payment.internal.refund.repository.RefundBudgetRepository;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.provider.RefundProviderRequest;
import com.shop.payment.provider.RefundProviderResult;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RefundTransactionService {
    private final PaymentAttemptRepository payments;
    private final RefundBudgetRepository budgets;
    private final RefundAttemptRepository refunds;
    private final RefundMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    PreparedRefund prepare(UUID paymentId, RefundCommand command, String actor, Set<String> supportedProviders) {
        PaymentAttempt payment = lockPayment(paymentId);
        RefundAttempt previous = refunds.findByPaymentAttemptIdAndKeyHash(paymentId, command.keyHash())
                .orElse(null);
        if (previous != null) {
            if (!previous.getFingerprint().equals(command.fingerprint())) {
                throw new AppException(ErrorCode.PAYMENT_REFUND_KEY_CONFLICT);
            }
            return new PreparedRefund(mapper.toResponse(previous), previous.getProviderCode(), null);
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_NOT_CAPTURED);
        }
        if (!payment.getCurrency().equals(command.currency())) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_DATA_INVALID);
        }
        if (!supportedProviders.contains(payment.getProviderCode())) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_PROVIDER_UNSUPPORTED);
        }
        Instant now = now();
        RefundBudget budget = budgets.findById(paymentId)
                .orElseGet(() -> RefundBudget.open(paymentId, payment.getAmount(), payment.getCurrency(), now));
        BigDecimal amount = command.amount() == null ? budget.remainingAmount() : command.amount();
        if (amount.signum() == 0) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_LIMIT_EXCEEDED);
        }
        budget.reserve(amount, now);
        budgets.saveAndFlush(budget);
        RefundAttempt refund = refunds.saveAndFlush(
                RefundAttempt.create(payment, amount, command.keyHash(), command.fingerprint(), actor, now));
        return new PreparedRefund(
                mapper.toResponse(refund),
                refund.getProviderCode(),
                new RefundProviderRequest(
                        refund.getId(),
                        paymentId,
                        payment.getOrderId(),
                        refund.getProviderPaymentReference(),
                        amount,
                        refund.getCurrency(),
                        RefundConstants.PROVIDER_KEY_PREFIX + refund.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    RefundResponse apply(UUID paymentId, UUID refundId, RefundProviderResult result) {
        lockPayment(paymentId);
        RefundAttempt refund = requireRefund(paymentId, refundId);
        if (refund.apply(result, now())) {
            if (refund.getStatus().isTerminal()) {
                RefundBudget budget = budgets.findById(paymentId).orElseThrow();
                budget.settle(refund.getAmount(), refund.getStatus() == RefundStatus.SUCCEEDED, refund.getUpdatedAt());
                budgets.saveAndFlush(budget);
            }
            refunds.saveAndFlush(refund);
        }
        return mapper.toResponse(refund);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    RefundResponse get(UUID paymentId, UUID refundId) {
        return mapper.toResponse(requireRefund(paymentId, refundId));
    }

    private PaymentAttempt lockPayment(UUID paymentId) {
        return payments.findForRefundUpdate(paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    }

    private RefundAttempt requireRefund(UUID paymentId, UUID refundId) {
        return refunds.findByIdAndPaymentAttemptId(refundId, paymentId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_REFUND_NOT_FOUND));
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
