package com.shop.catalog.internal.image.dto.response;

import java.net.URI;
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
public class ProductImageResponse {
    UUID id;
    String objectKey;
    URI url;
    String originalFilename;
    String contentType;
    long sizeBytes;
    boolean primary;
    int displayOrder;
    long version;
    Instant createdAt;
}
