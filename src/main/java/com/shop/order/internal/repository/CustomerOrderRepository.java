package com.shop.order.internal.repository;

import com.shop.order.internal.entity.CustomerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CustomerOrderRepository extends Repository<CustomerOrder, UUID> {

    <S extends CustomerOrder> S saveAndFlush(S order);

    Optional<CustomerOrder> findById(UUID id);
}
