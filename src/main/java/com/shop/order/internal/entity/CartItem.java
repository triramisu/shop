package com.shop.order.internal.entity;

import com.shop.order.internal.constant.OrderTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;

@Getter
@Entity
@Table(name = OrderTableNames.CART_ITEMS)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private Cart cart;

    @Column(name = "product_variant_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(nullable = false, updatable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {}

    CartItem(Cart cart, UUID productVariantId, String sku, int quantity) {
        this.cart = Objects.requireNonNull(cart, "cart is required");
        this.productVariantId = Objects.requireNonNull(productVariantId, "product variant id is required");
        this.sku = CartDomainRules.sku(sku);
        this.quantity = CartDomainRules.quantity(quantity);
    }

    void increase(int delta) {
        quantity = CartDomainRules.addQuantity(quantity, delta);
    }

    void updateQuantity(int newQuantity) {
        quantity = CartDomainRules.quantity(newQuantity);
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
