/**
 * Stock levels, movements and time-bound stock reservations.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"catalog :: inventory", "shared :: error", "shared :: web"})
package com.shop.inventory;
