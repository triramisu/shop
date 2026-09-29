package com.shop.catalog.internal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDomainTests {

    @Test
    void normalizesIdentifiersAndPublishesOnlyWithASellableVariant() {
        Category category = Category.create(" electronics ", " Electronics ", "electronics");
        Product product = Product.create(category, " Laptop Pro ", "laptop-pro", "  Portable workstation  ");

        assertThat(category.getCode()).isEqualTo("ELECTRONICS");
        assertThat(product.getName()).isEqualTo("Laptop Pro");
        assertThat(product.getDescription()).isEqualTo("Portable workstation");
        assertThatThrownBy(product::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active variant");

        ProductVariant variant = product.addVariant(" laptop-pro.16gb ", "16 GB", new BigDecimal("1299.9"), "usd");
        product.publish();

        assertThat(variant.getSku()).isEqualTo("LAPTOP-PRO.16GB");
        assertThat(variant.getPrice()).isEqualByComparingTo("1299.90");
        assertThat(variant.getCurrency()).isEqualTo("USD");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(product.getVariants()).containsExactly(variant);
        List<ProductVariant> variants = product.getVariants();
        assertThatThrownBy(variants::clear).isInstanceOf(UnsupportedOperationException.class);

        product.hide();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void rejectsAnInvalidCategoryDuplicateSkuAndInvalidMoney() {
        Category inactiveCategory = Category.create("BOOKS", "Books", "books");
        inactiveCategory.deactivate();

        assertThatThrownBy(() -> Product.create(null, "Book", "book", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("category");
        assertThatThrownBy(() -> Product.create(inactiveCategory, "Book", "book", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active");

        Category category = Category.create("COMPUTERS", "Computers", "computers");
        Product product = Product.create(category, "Laptop", "laptop", null);
        BigDecimal negativePrice = new BigDecimal("-0.01");
        BigDecimal overPrecisionPrice = new BigDecimal("1.001");
        product.addVariant("LAPTOP-BASE", "Base", BigDecimal.ZERO, "VND");

        assertThatThrownBy(() -> product.addVariant("laptop-base", "Duplicate", BigDecimal.ONE, "VND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU");
        assertThatThrownBy(() -> product.addVariant("NEGATIVE", "Invalid", negativePrice, "VND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
        assertThatThrownBy(() -> product.addVariant("PRECISION", "Invalid", overPrecisionPrice, "VND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimal");
        assertThatThrownBy(() -> product.addVariant("CURRENCY", "Invalid", BigDecimal.ONE, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
        assertThatThrownBy(() -> product.addVariant("NO-CURRENCY", "Invalid", BigDecimal.ONE, "XXX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void softDeletingAProductArchivesItsVariantsAndMakesTheAggregateImmutable() {
        Category category = Category.create("PHONES", "Phones", "phones");
        Product product = Product.create(category, "Phone", "phone", null);
        ProductVariant variant = product.addVariant("PHONE-BLACK", "Black", new BigDecimal("500.00"), "USD");
        Instant deletedAt = Instant.parse("2026-09-28T12:00:00Z");

        product.softDelete(deletedAt);
        product.softDelete(deletedAt.plusSeconds(1));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(product.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.ARCHIVED);
        assertThat(variant.getDeletedAt()).isEqualTo(deletedAt);
        assertThatThrownBy(() -> product.addVariant("PHONE-WHITE", "White", BigDecimal.ONE, "USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Archived");
        assertThatThrownBy(() -> variant.updateDetails("Changed", BigDecimal.ONE, "USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Archived");
    }
}
