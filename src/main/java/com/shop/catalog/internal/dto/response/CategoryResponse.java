package com.shop.catalog.internal.dto.response;

import com.shop.catalog.internal.entity.CategoryStatus;
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
public class CategoryResponse {
    UUID id;
    String code;
    String name;
    String slug;
    CategoryStatus status;
    long version;
    Instant createdAt;
    Instant updatedAt;
}
