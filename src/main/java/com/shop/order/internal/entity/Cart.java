package com.shop.order.internal.entity;

import static com.shop.order.internal.constant.CartValidationConstants.MAX_DISTINCT_ITEMS;

import com.shop.order.internal.constant.OrderTableNames;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = OrderTableNames.CARTS)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 100)
    private String ownerSubject;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    @Getter(AccessLevel.NONE)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {}

    @Builder(access = AccessLevel.PRIVATE)
    private Cart(String ownerSubject) {
        this.ownerSubject = CartDomainRules.ownerSubject(ownerSubject);
    }

    public static Cart create(String ownerSubject) {
        return Cart.builder().ownerSubject(ownerSubject).build();
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Optional<CartItem> findItem(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }

    public CartItem addOrIncrement(UUID productVariantId, String sku, int quantity) {
        CartItem existing = items.stream()
                .filter(item -> item.getProductVariantId().equals(productVariantId))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.increase(quantity);
            markChanged();
            return existing;
        }
        if (items.size() >= MAX_DISTINCT_ITEMS) {
            throw new CartLimitExceededException(CartLimitExceededException.LimitType.DISTINCT_ITEMS);
        }
        CartItem item = new CartItem(this, productVariantId, sku, quantity);
        items.add(item);
        markChanged();
        return item;
    }

    public void updateQuantity(UUID itemId, int quantity) {
        CartItem item = findItem(itemId).orElseThrow();
        item.updateQuantity(quantity);
        markChanged();
    }

    public void removeItem(UUID itemId) {
        CartItem item = findItem(itemId).orElseThrow();
        items.remove(item);
        markChanged();
    }

    public void clear() {
        if (!items.isEmpty()) {
            items.clear();
            markChanged();
        }
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
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

    private void markChanged() {
        updatedAt = Instant.now();
    }
}
