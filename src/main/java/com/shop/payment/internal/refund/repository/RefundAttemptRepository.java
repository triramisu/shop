package com.shop.payment.internal.refund.repository;

import com.shop.payment.internal.refund.entity.RefundAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface RefundAttemptRepository extends Repository<RefundAttempt, UUID> {
    Optional<RefundAttempt> findByIdAndPaymentAttemptId(UUID id, UUID paymentAttemptId);

    Optional<RefundAttempt> findByPaymentAttemptIdAndKeyHash(UUID paymentAttemptId, String keyHash);

    RefundAttempt saveAndFlush(RefundAttempt refund);
}
