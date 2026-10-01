/**
 * Shopping carts, checkout orchestration, orders and order item snapshots.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"catalog :: order", "shared :: error", "shared :: web"})
package com.shop.order;
