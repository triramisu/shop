package com.shop.catalog.internal.dto.response;

import java.util.List;
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
public class ProductPageResponse {
    List<ProductResponse> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean first;
    boolean last;
}
