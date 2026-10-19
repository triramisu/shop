package com.shop.catalog.internal.storefront.dto.response;

import java.net.URI;
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
public class StorefrontProductImageResponse {
    UUID id;
    URI url;
    String contentType;
    boolean primary;
    int displayOrder;
}
