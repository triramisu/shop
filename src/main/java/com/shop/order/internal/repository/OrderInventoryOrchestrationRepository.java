package com.shop.order.internal.repository;

import com.shop.order.internal.checkout.orchestration.OrderInventoryOrchestration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

public interface OrderInventoryOrchestrationRepository extends Repository<OrderInventoryOrchestration, UUID> {

    <S extends OrderInventoryOrchestration> S saveAndFlush(S orchestration);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "lines"})
    Optional<OrderInventoryOrchestration> findByRequestEventId(UUID requestEventId);

    @EntityGraph(attributePaths = {"order", "lines"})
    Optional<OrderInventoryOrchestration> findByOrder_Id(UUID orderId);

    @EntityGraph(attributePaths = {"order", "lines"})
    Optional<OrderInventoryOrchestration> findByCorrelationId(UUID correlationId);
}
