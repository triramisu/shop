package com.shop.order.internal.repository;

import com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus;
import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OrderInventoryOrchestrationRepository extends Repository<OrderInventoryOrchestration, UUID> {

    <S extends OrderInventoryOrchestration> S saveAndFlush(S orchestration);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "lines"})
    Optional<OrderInventoryOrchestration> findByRequestEventId(UUID requestEventId);

    @EntityGraph(attributePaths = {"order", "lines"})
    Optional<OrderInventoryOrchestration> findByOrder_Id(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "lines"})
    @Query(
            "select orchestration from OrderInventoryOrchestration orchestration where orchestration.order.id = :orderId")
    Optional<OrderInventoryOrchestration> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = {"order", "lines"})
    Optional<OrderInventoryOrchestration> findByCorrelationId(UUID correlationId);

    @Query("""
            select orchestration.requestEventId
              from OrderInventoryOrchestration orchestration
             where orchestration.status in :statuses
               and orchestration.updatedAt <= :staleBefore
             order by orchestration.updatedAt, orchestration.id
            """)
    List<UUID> findRecoveryCandidateRequestEventIds(
            @Param("statuses") List<InventoryOrchestrationStatus> statuses,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);

    @Query("""
            select orchestration.order.id
              from OrderInventoryOrchestration orchestration
             where orchestration.status = :status
               and orchestration.updatedAt <= :staleBefore
             order by orchestration.updatedAt, orchestration.id
            """)
    List<UUID> findPaymentInitiationCandidateOrderIds(
            @Param("status") InventoryOrchestrationStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);
}
