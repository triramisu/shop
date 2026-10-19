package com.shop.payment.internal.repository;

import com.shop.payment.internal.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface PaymentAttemptRepository extends Repository<PaymentAttempt, UUID> {

    <S extends PaymentAttempt> S saveAndFlush(S paymentAttempt);

    Optional<PaymentAttempt> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p where p.id = :id")
    Optional<PaymentAttempt> findForRefundUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p where p.id = :id")
    Optional<PaymentAttempt> findForProviderUpdate(UUID id);

    Optional<PaymentAttempt> findByOrderIdAndAttemptNumber(UUID orderId, int attemptNumber);

    Optional<PaymentAttempt> findByProviderCodeAndProviderReference(String providerCode, String providerReference);

    List<PaymentAttempt> findByOrderIdOrderByAttemptNumber(UUID orderId);

    Optional<PaymentAttempt> findFirstByOrderIdOrderByAttemptNumberDesc(UUID orderId);
}
