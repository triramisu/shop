package com.shop.order.internal.payment.repository;

import com.shop.order.internal.payment.entity.OrderPaymentEventOutcome;
import com.shop.order.internal.payment.entity.OrderPaymentEventRecord;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OrderPaymentEventRepository extends Repository<OrderPaymentEventRecord, UUID> {

    <S extends OrderPaymentEventRecord> S saveAndFlush(S event);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OrderPaymentEventRecord> findByEventId(UUID eventId);

    @Query("""
            select paymentEvent.eventId
              from OrderPaymentEventRecord paymentEvent
             where paymentEvent.outcome in :outcomes
               and coalesce(paymentEvent.processedAt, paymentEvent.receivedAt) <= :staleBefore
             order by coalesce(paymentEvent.processedAt, paymentEvent.receivedAt), paymentEvent.id
            """)
    List<UUID> findRecoveryCandidateEventIds(
            @Param("outcomes") List<OrderPaymentEventOutcome> outcomes,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);
}
