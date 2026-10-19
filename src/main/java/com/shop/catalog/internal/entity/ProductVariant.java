package com.shop.catalog.internal.entity;

import com.shop.catalog.internal.constant.CatalogTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = CatalogTableNames.PRODUCT_VARIANTS)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, updatable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductVariantStatus status = ProductVariantStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ProductVariant() {}

    @Builder(access = AccessLevel.PRIVATE)
    private ProductVariant(Product product, String sku, String name, BigDecimal price, String currency) {
        this.product = Objects.requireNonNull(product);
        this.sku = CatalogDomainRules.sku(sku);
        this.name = CatalogDomainRules.requiredText(name, "variant name", 200);
        this.price = CatalogDomainRules.price(price);
        this.currency = CatalogDomainRules.currency(currency);
    }

    static ProductVariant create(Product product, String sku, String name, BigDecimal price, String currency) {
        return ProductVariant.builder()
                .product(product)
                .sku(sku)
                .name(name)
                .price(price)
                .currency(currency)
                .build();
    }

    void updateDetails(String name, BigDecimal price, String currency) {
        requireNotArchived();
        this.name = CatalogDomainRules.requiredText(name, "variant name", 200);
        this.price = CatalogDomainRules.price(price);
        this.currency = CatalogDomainRules.currency(currency);
    }

    void activate() {
        requireNotArchived();
        status = ProductVariantStatus.ACTIVE;
    }

    void deactivate() {
        requireNotArchived();
        status = ProductVariantStatus.INACTIVE;
    }

    public void softDelete(Instant occurredAt) {
        if (deletedAt != null) {
            return;
        }
        deletedAt = Objects.requireNonNull(occurredAt);
        status = ProductVariantStatus.ARCHIVED;
    }

    public boolean isSellable() {
        return status == ProductVariantStatus.ACTIVE && deletedAt == null;
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

    private void requireNotArchived() {
        if (status == ProductVariantStatus.ARCHIVED || deletedAt != null) {
            throw new IllegalStateException("Archived variants cannot be changed");
        }
    }
}
