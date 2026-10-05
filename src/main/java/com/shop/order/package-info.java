/**
 * Shopping carts, checkout orchestration, orders and order item snapshots.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
            "catalog :: order",
            "inventory :: reservation",
            "payment :: event",
            "payment :: processing",
            "shared :: error",
            "shared :: web"
        })
package com.shop.order;
