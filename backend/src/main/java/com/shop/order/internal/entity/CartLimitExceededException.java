package com.shop.order.internal.entity;

import lombok.Getter;

@Getter
public class CartLimitExceededException extends RuntimeException {

    private final LimitType limitType;

    CartLimitExceededException(LimitType limitType) {
        super(limitType.name());
        this.limitType = limitType;
    }

    public enum LimitType {
        QUANTITY,
        DISTINCT_ITEMS
    }
}
