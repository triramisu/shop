package com.shop.payment.internal.webhook.repository;

import com.shop.payment.internal.webhook.entity.PaymentWebhookEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface PaymentWebhookEventRepository extends Repository<PaymentWebhookEvent, UUID> {

    <S extends PaymentWebhookEvent> S saveAndFlush(S event);

    Optional<PaymentWebhookEvent> findByProviderCodeAndProviderEventId(String providerCode, String providerEventId);

    long count();
}
