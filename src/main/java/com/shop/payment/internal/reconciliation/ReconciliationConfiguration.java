package com.shop.payment.internal.reconciliation;

import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.payment.internal.service.PaymentLifecycleEventPublisher;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.payment.reconciliation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ReconciliationProperties.class)
class ReconciliationConfiguration {
    @Bean
    ReconciliationRepository reconciliationRepository(
            JdbcTemplate jdbc,
            ReconciliationProperties properties,
            Environment environment,
            ObjectProvider<PaymentStatusQueryOperations> provider) {
        properties.validate();
        if (!"vnpay".equals(environment.getProperty("app.payment.provider.type"))
                || !environment.getProperty("app.payment.vnpay.query.enabled", Boolean.class, false)
                || provider.getIfUnique() == null
                || !"VNPAY".equals(provider.getIfUnique().providerCode())) {
            throw new IllegalStateException("Reconciliation requires the VNPay sandbox query adapter to be enabled");
        }
        return new ReconciliationRepository(jdbc);
    }

    @Bean
    ReconciliationStore reconciliationStore(
            ReconciliationRepository repository,
            PaymentAttemptRepository attempts,
            PaymentLifecycleEventPublisher publisher,
            ReconciliationProperties properties) {
        return new ReconciliationStore(repository, attempts, publisher, properties);
    }

    @Bean
    Clock paymentReconciliationClock() {
        return Clock.systemUTC();
    }

    @Bean
    ReconciliationProcessor reconciliationProcessor(
            ReconciliationStore store,
            ReconciliationRepository repository,
            PaymentStatusQueryOperations provider,
            ReconciliationProperties properties,
            MeterRegistry meters,
            @Qualifier("paymentReconciliationClock") Clock clock) {
        return new ReconciliationProcessor(store, repository, provider, properties, meters, clock);
    }

    @Bean
    ReconciliationJob reconciliationJob(ReconciliationProcessor processor, MeterRegistry meters) {
        return new ReconciliationJob(processor, meters);
    }
}
