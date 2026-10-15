package com.shop.order.internal.repository;

import com.shop.order.internal.entity.CustomerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends Repository<CustomerOrder, UUID> {

    <S extends CustomerOrder> S saveAndFlush(S order);

    Optional<CustomerOrder> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    @Query("select shopOrder from CustomerOrder shopOrder where shopOrder.id = :orderId")
    Optional<CustomerOrder> findDetailedById(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = "items")
    @Query("""
            select shopOrder
              from CustomerOrder shopOrder
             where shopOrder.id = :orderId
               and shopOrder.ownerSubject = :ownerSubject
            """)
    Optional<CustomerOrder> findDetailedByIdAndOwnerSubject(
            @Param("orderId") UUID orderId, @Param("ownerSubject") String ownerSubject);
}
