package com.shop.inventory.internal.dto.response;

import com.shop.inventory.event.StockMovementType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StockMovementResponse {
    UUID id;
    StockMovementType movementType;
    long onHandDelta;
    long reservedDelta;
    long onHandAfter;
    long reservedAfter;
    String reason;
    String referenceId;
    Instant occurredAt;
}
