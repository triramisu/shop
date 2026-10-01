package com.shop.order.internal.dto.response;

import java.time.Instant;
import java.util.List;
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
public class CartResponse {
    UUID id;
    List<CartItemResponse> items;
    int distinctItemCount;
    int totalQuantity;
    long version;
    Instant updatedAt;
}
