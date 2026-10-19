package com.shop.order.internal.repository;

import com.shop.order.internal.checkout.idempotency.CheckoutIdempotencyOperation;
import com.shop.order.internal.checkout.idempotency.CheckoutIdempotencyRecord;
import com.shop.order.internal.checkout.idempotency.CheckoutIdempotencyStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CheckoutIdempotencyRepository extends Repository<CheckoutIdempotencyRecord, UUID> {

    <S extends CheckoutIdempotencyRecord> S saveAndFlush(S record);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CheckoutIdempotencyRecord> findByOwnerSubjectAndOperationAndIdempotencyKeyHash(
            String ownerSubject, CheckoutIdempotencyOperation operation, String idempotencyKeyHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CheckoutIdempotencyRecord> findByExecutionId(UUID executionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from CheckoutIdempotencyRecord record
             where record.expiresAt <= :expiredBefore
               and record.status <> :processing
            """)
    int purgeTerminalExpiredBefore(
            @Param("expiredBefore") Instant expiredBefore, @Param("processing") CheckoutIdempotencyStatus processing);

    long countByOwnerSubject(String ownerSubject);
}
