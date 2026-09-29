package com.shop.catalog.internal.image.entity;

import com.shop.catalog.internal.constant.CatalogTableNames;
import com.shop.catalog.internal.entity.Product;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = CatalogTableNames.PRODUCT_IMAGES)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "object_key", nullable = false, unique = true, length = 500, updatable = false)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 50, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "primary_image", nullable = false)
    private boolean primaryImage;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductImage() {}

    @Builder(access = AccessLevel.PRIVATE)
    private ProductImage(
            Product product,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            boolean primaryImage,
            int displayOrder) {
        this.product = Objects.requireNonNull(product, "product is required");
        this.objectKey = requireText(objectKey, "object key", 500);
        this.originalFilename = requireText(originalFilename, "original filename", 255);
        this.contentType = requireSupportedContentType(contentType);
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("image size must be positive");
        }
        if (displayOrder < 0) {
            throw new IllegalArgumentException("image display order cannot be negative");
        }
        this.sizeBytes = sizeBytes;
        this.primaryImage = primaryImage;
        this.displayOrder = displayOrder;
    }

    public static ProductImage create(
            Product product,
            String objectKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            boolean primaryImage,
            int displayOrder) {
        return ProductImage.builder()
                .product(product)
                .objectKey(objectKey)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .sizeBytes(sizeBytes)
                .primaryImage(primaryImage)
                .displayOrder(displayOrder)
                .build();
    }

    public void arrange(boolean primaryImage, int displayOrder) {
        if (displayOrder < 0) {
            throw new IllegalArgumentException("image display order cannot be negative");
        }
        this.primaryImage = primaryImage;
        this.displayOrder = displayOrder;
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

    private static String requireSupportedContentType(String contentType) {
        String requiredContentType = requireText(contentType, "content type", 50);
        if (!requiredContentType.equals("image/jpeg") && !requiredContentType.equals("image/png")) {
            throw new IllegalArgumentException("image content type is unsupported");
        }
        return requiredContentType;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        String requiredValue =
                Objects.requireNonNull(value, fieldName + " is required").strip();
        if (requiredValue.isEmpty() || requiredValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return requiredValue;
    }
}
