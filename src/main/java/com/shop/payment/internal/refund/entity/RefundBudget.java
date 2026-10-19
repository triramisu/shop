package com.shop.payment.internal.refund.entity;

import com.shop.payment.internal.constant.PaymentTableNames;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = PaymentTableNames.REFUND_BUDGETS)
public class RefundBudget {
    @Id
    @Column(name = "payment_attempt_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID paymentAttemptId;

    @Column(name = "captured_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal capturedAmount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "succeeded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal succeededAmount = BigDecimal.ZERO.setScale(2);

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RefundBudget() {}

    public static RefundBudget open(UUID paymentAttemptId, BigDecimal capturedAmount, String currency, Instant now) {
        RefundBudget budget = new RefundBudget();
        budget.paymentAttemptId = java.util.Objects.requireNonNull(paymentAttemptId);
        budget.capturedAmount = capturedAmount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (budget.capturedAmount.signum() <= 0
                || budget.capturedAmount.precision() > 19
                || currency == null
                || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Ngân sách hoàn tiền không hợp lệ");
        }
        budget.currency = currency;
        budget.createdAt = java.util.Objects.requireNonNull(now);
        budget.updatedAt = now;
        return budget;
    }

    public BigDecimal remainingAmount() {
        return capturedAmount.subtract(allocatedAmount);
    }

    public void reserve(BigDecimal amount, Instant now) {
        requireAmount(amount);
        if (amount.compareTo(remainingAmount()) > 0) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_LIMIT_EXCEEDED);
        }
        touch(now);
        allocatedAmount = allocatedAmount.add(amount);
    }

    public void settle(BigDecimal amount, boolean success, Instant now) {
        requireAmount(amount);
        if (amount.compareTo(allocatedAmount.subtract(succeededAmount)) > 0) {
            throw new IllegalStateException("Khoản hoàn vượt số tiền đang được giữ");
        }
        touch(now);
        if (success) {
            succeededAmount = succeededAmount.add(amount);
        } else {
            allocatedAmount = allocatedAmount.subtract(amount);
        }
    }

    private void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() > 19) {
            throw new AppException(ErrorCode.PAYMENT_REFUND_DATA_INVALID);
        }
    }

    private void touch(Instant now) {
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Thời điểm hoàn tiền không được lùi");
        }
        updatedAt = now;
    }
}
