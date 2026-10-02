package com.shop.payment.internal.repository;

import com.shop.payment.internal.entity.PaymentAttempt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface PaymentAttemptRepository extends Repository<PaymentAttempt, UUID> {

    <S extends PaymentAttempt> S saveAndFlush(S paymentAttempt);

    Optional<PaymentAttempt> findById(UUID id);

    Optional<PaymentAttempt> findByOrderIdAndAttemptNumber(UUID orderId, int attemptNumber);

    Optional<PaymentAttempt> findByProviderCodeAndProviderReference(String providerCode, String providerReference);

    List<PaymentAttempt> findByOrderIdOrderByAttemptNumber(UUID orderId);
}
