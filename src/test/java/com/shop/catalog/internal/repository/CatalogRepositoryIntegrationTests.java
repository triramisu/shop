package com.shop.catalog.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.catalog.internal.entity.Category;
import com.shop.catalog.internal.entity.Product;
import com.shop.catalog.internal.entity.ProductStatus;
import com.shop.catalog.internal.entity.ProductVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogRepositoryIntegrationTests {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndLoadsTheProductAggregateWithExplicitAuditFields() {
        Category category = categoryRepository.save(Category.create("LAPTOP", "Laptop", "laptop"));
        Product product = Product.create(category, "Developer Laptop", "developer-laptop", "Workstation");
        ProductVariant variant = product.addVariant("DEV-LAPTOP-32", "32 GB", new BigDecimal("1999.00"), "USD");
        product.publish();
        productRepository.save(product);
        entityManager.flush();

        assertThat(product.getId()).isNotNull();
        assertThat(variant.getId()).isNotNull();
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(product.getUpdatedAt()).isNotNull();
        assertThat(productVariantRepository.existsBySkuIgnoreCase("dev-laptop-32"))
                .isTrue();
        assertThat(productRepository.existsByCategoryIdAndDeletedAtIsNull(category.getId()))
                .isTrue();

        entityManager.clear();
        Product detailed = productRepository.findDetailedById(product.getId()).orElseThrow();
        assertThat(detailed.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(detailed.getCategory().getCode()).isEqualTo("LAPTOP");
        assertThat(detailed.getVariants()).extracting(ProductVariant::getSku).containsExactly("DEV-LAPTOP-32");
    }

    @Test
    void keepsSkuGloballyUniqueEvenWhenInputUsesDifferentLetterCase() {
        Category category = categoryRepository.save(Category.create("AUDIO", "Audio", "audio"));
        Product first = Product.create(category, "Headphones", "headphones", null);
        first.addVariant("audio-headset-01", "Black", new BigDecimal("49.90"), "USD");
        Product second = Product.create(category, "Gaming Headphones", "gaming-headphones", null);
        second.addVariant("AUDIO-HEADSET-01", "Red", new BigDecimal("59.90"), "USD");
        productRepository.save(first);
        productRepository.save(second);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }
}
