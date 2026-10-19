package com.shop.payment.internal.refund.repository;

import com.shop.payment.internal.refund.entity.RefundBudget;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface RefundBudgetRepository extends Repository<RefundBudget, UUID> {
    Optional<RefundBudget> findById(UUID paymentAttemptId);

    RefundBudget saveAndFlush(RefundBudget budget);
}
