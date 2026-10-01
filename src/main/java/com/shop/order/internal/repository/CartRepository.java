package com.shop.order.internal.repository;

import com.shop.order.internal.entity.Cart;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends Repository<Cart, UUID> {

    <S extends Cart> S saveAndFlush(S cart);

    boolean existsByOwnerSubject(String ownerSubject);

    @EntityGraph(attributePaths = "items")
    @Query("select cart from Cart cart where cart.ownerSubject = :ownerSubject")
    Optional<Cart> findDetailedByOwnerSubject(@Param("ownerSubject") String ownerSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cart from Cart cart where cart.ownerSubject = :ownerSubject")
    Optional<Cart> findByOwnerSubjectForUpdate(@Param("ownerSubject") String ownerSubject);
}
