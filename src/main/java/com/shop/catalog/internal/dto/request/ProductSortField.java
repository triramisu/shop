package com.shop.catalog.internal.dto.request;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductSortField {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    NAME("name");

    private final String property;
}
