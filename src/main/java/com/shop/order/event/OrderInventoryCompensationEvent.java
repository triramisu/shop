package com.shop.order.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderInventoryCompensationEvent(
        UUID eventId,
        UUID requestEventId,
        UUID correlationId,
        UUID orderId,
        OrderInventoryCompensationStatus status,
        String failureCode,
        List<UUID> releasedReservationIds,
        List<UUID> unresolvedReservationIds,
        Instant occurredAt) {

    public OrderInventoryCompensationEvent {
        Objects.requireNonNull(eventId, "event id is required");
        Objects.requireNonNull(requestEventId, "request event id is required");
        Objects.requireNonNull(correlationId, "correlation id is required");
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(status, "compensation status is required");
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failure code is required");
        }
        failureCode = failureCode.strip();
        releasedReservationIds =
                List.copyOf(Objects.requireNonNull(releasedReservationIds, "released reservation ids are required"));
        unresolvedReservationIds = List.copyOf(
                Objects.requireNonNull(unresolvedReservationIds, "unresolved reservation ids are required"));
        Objects.requireNonNull(occurredAt, "occurrence time is required");
        if (releasedReservationIds.stream().distinct().count() != releasedReservationIds.size()
                || unresolvedReservationIds.stream().distinct().count() != unresolvedReservationIds.size()
                || releasedReservationIds.stream().anyMatch(unresolvedReservationIds::contains)) {
            throw new IllegalArgumentException("compensation reservation ids must be unique and disjoint");
        }
        if (status == OrderInventoryCompensationStatus.COMPLETED && !unresolvedReservationIds.isEmpty()) {
            throw new IllegalArgumentException("completed compensation cannot contain unresolved reservations");
        }
        if (status == OrderInventoryCompensationStatus.REQUIRED && unresolvedReservationIds.isEmpty()) {
            throw new IllegalArgumentException("required compensation must contain unresolved reservations");
        }
    }
}
