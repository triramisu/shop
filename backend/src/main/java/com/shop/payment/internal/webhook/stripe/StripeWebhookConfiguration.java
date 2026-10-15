package com.shop.payment.internal.webhook.stripe;

import com.shop.payment.internal.provider.stripe.StripePaymentProviderProperties;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.internal.webhook.repository.PaymentWebhookEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.provider", name = "type", havingValue = "stripe")
class StripeWebhookConfiguration {

    @Bean
    StripeWebhookSignatureVerifier stripeWebhookSignatureVerifier(StripePaymentProviderProperties properties) {
        return new StripeWebhookSignatureVerifier(
                properties.getWebhookSecrets(), properties.getWebhookSignatureTolerance(), Clock.systemUTC());
    }

    @Bean
    StripeWebhookEventParser stripeWebhookEventParser(ObjectMapper objectMapper) {
        return new StripeWebhookEventParser(objectMapper);
    }

    @Bean
    StripeWebhookTransactionService stripeWebhookTransactionService(
            PaymentWebhookEventRepository webhookEventRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentLifecycleEventPublisher lifecycleEventPublisher,
            StripePaymentProviderProperties properties) {
        return new StripeWebhookTransactionService(
                webhookEventRepository,
                paymentAttemptRepository,
                lifecycleEventPublisher,
                properties.getApiVersion(),
                properties.getSecretKey().startsWith("sk_live_"));
    }

    @Bean
    StripeWebhookMetrics stripeWebhookMetrics(MeterRegistry meterRegistry) {
        return new StripeWebhookMetrics(meterRegistry);
    }

    @Bean
    StripeWebhookService stripeWebhookService(
            StripeWebhookSignatureVerifier signatureVerifier,
            StripeWebhookEventParser eventParser,
            StripeWebhookTransactionService transactionService,
            PaymentWebhookEventRepository webhookEventRepository,
            StripeWebhookMetrics metrics) {
        return new StripeWebhookService(
                signatureVerifier, eventParser, transactionService, webhookEventRepository, metrics, Clock.systemUTC());
    }
}
