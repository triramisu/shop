package com.shop.catalog.internal.entity;

import com.shop.catalog.internal.constant.CatalogTableNames;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

@Getter
@Entity
@Table(name = CatalogTableNames.PRODUCTS)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Getter(AccessLevel.NONE)
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {}

    @Builder(access = AccessLevel.PRIVATE)
    private Product(Category category, String name, String slug, String description) {
        this.category = requireAvailableCategory(category);
        this.name = CatalogDomainRules.requiredText(name, "product name", 200);
        this.slug = CatalogDomainRules.slug(slug, 220);
        this.description = CatalogDomainRules.optionalText(description, "product description", 5000);
    }

    public static Product create(Category category, String name, String slug, String description) {
        return Product.builder()
                .category(category)
                .name(name)
                .slug(slug)
                .description(description)
                .build();
    }

    public void updateDetails(Category category, String name, String slug, String description) {
        requireNotArchived();
        this.category = requireAvailableCategory(category);
        this.name = CatalogDomainRules.requiredText(name, "product name", 200);
        this.slug = CatalogDomainRules.slug(slug, 220);
        this.description = CatalogDomainRules.optionalText(description, "product description", 5000);
    }

    public ProductVariant addVariant(String sku, String name, BigDecimal price, String currency) {
        requireNotArchived();
        String normalizedSku = CatalogDomainRules.sku(sku);
        if (variants.stream().anyMatch(variant -> variant.getSku().equals(normalizedSku))) {
            throw new IllegalArgumentException("Product already contains this SKU");
        }
        ProductVariant variant = ProductVariant.create(this, normalizedSku, name, price, currency);
        variants.add(variant);
        markChanged();
        return variant;
    }

    public ProductVariant updateVariant(UUID variantId, String name, BigDecimal price, String currency) {
        requireNotArchived();
        ProductVariant variant = getRequiredVariant(variantId);
        variant.updateDetails(name, price, currency);
        return variant;
    }

    public ProductVariant changeVariantStatus(UUID variantId, ProductVariantStatus newStatus) {
        requireNotArchived();
        ProductVariant variant = getRequiredVariant(variantId);
        ProductVariantStatus requiredStatus = Objects.requireNonNull(newStatus, "variant status is required");
        if (requiredStatus == ProductVariantStatus.ARCHIVED) {
            throw new IllegalArgumentException("Variant cannot be archived through a status change");
        }
        if (requiredStatus == ProductVariantStatus.INACTIVE
                && status == ProductStatus.PUBLISHED
                && variant.isSellable()
                && variants.stream().filter(ProductVariant::isSellable).count() == 1) {
            throw new IllegalStateException("A published product must keep at least one active variant");
        }
        if (requiredStatus == ProductVariantStatus.ACTIVE) {
            variant.activate();
        } else {
            variant.deactivate();
        }
        markChanged();
        return variant;
    }

    public void publish() {
        requireNotArchived();
        if (!category.isAvailable()) {
            throw new IllegalStateException("A product requires an active category before publishing");
        }
        if (variants.stream().noneMatch(ProductVariant::isSellable)) {
            throw new IllegalStateException("A product requires at least one active variant before publishing");
        }
        status = ProductStatus.PUBLISHED;
    }

    public void hide() {
        requireNotArchived();
        if (status != ProductStatus.PUBLISHED) {
            throw new IllegalStateException("Only published products can be hidden");
        }
        status = ProductStatus.HIDDEN;
    }

    public void softDelete(Instant occurredAt) {
        if (deletedAt != null) {
            return;
        }
        Instant requiredOccurredAt = Objects.requireNonNull(occurredAt);
        deletedAt = requiredOccurredAt;
        status = ProductStatus.ARCHIVED;
        variants.forEach(variant -> variant.softDelete(requiredOccurredAt));
    }

    public List<ProductVariant> getVariants() {
        return Collections.unmodifiableList(variants);
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

    private static Category requireAvailableCategory(Category category) {
        Category requiredCategory = Objects.requireNonNull(category, "category is required");
        if (!requiredCategory.isAvailable()) {
            throw new IllegalArgumentException("category must be active");
        }
        return requiredCategory;
    }

    private void requireNotArchived() {
        if (status == ProductStatus.ARCHIVED || deletedAt != null) {
            throw new IllegalStateException("Archived products cannot be changed");
        }
    }

    private ProductVariant getRequiredVariant(UUID variantId) {
        return variants.stream()
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Variant does not belong to this product"));
    }

    private void markChanged() {
        updatedAt = Instant.now();
    }
}
