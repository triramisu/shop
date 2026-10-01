package com.shop.order.internal.entity;

import lombok.Getter;

@Getter
public class OrderTransitionRejectedException extends RuntimeException {

    private final Reason reason;

    OrderTransitionRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public enum Reason {
        STATE,
        ACTOR
    }
}
