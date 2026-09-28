package com.shop.catalog.internal.entity;

import com.shop.catalog.internal.constant.CatalogTableNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = CatalogTableNames.CATEGORIES)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoryStatus status = CategoryStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Category() {}

    @Builder(access = AccessLevel.PRIVATE)
    private Category(String code, String name, String slug) {
        this.code = CatalogDomainRules.code(code, "category code", 50);
        this.name = CatalogDomainRules.requiredText(name, "category name", 150);
        this.slug = CatalogDomainRules.slug(slug, 180);
    }

    public static Category create(String code, String name, String slug) {
        return Category.builder().code(code).name(name).slug(slug).build();
    }

    public void updateDetails(String name, String slug) {
        requireNotDeleted();
        this.name = CatalogDomainRules.requiredText(name, "category name", 150);
        this.slug = CatalogDomainRules.slug(slug, 180);
    }

    public void activate() {
        requireNotDeleted();
        status = CategoryStatus.ACTIVE;
    }

    public void deactivate() {
        requireNotDeleted();
        status = CategoryStatus.INACTIVE;
    }

    public void softDelete(Instant occurredAt) {
        if (deletedAt != null) {
            return;
        }
        deletedAt = Objects.requireNonNull(occurredAt);
        status = CategoryStatus.INACTIVE;
    }

    public boolean isAvailable() {
        return status == CategoryStatus.ACTIVE && deletedAt == null;
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

    private void requireNotDeleted() {
        if (deletedAt != null) {
            throw new IllegalStateException("Deleted categories cannot be changed");
        }
    }
}
