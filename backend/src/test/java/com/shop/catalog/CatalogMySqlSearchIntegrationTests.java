package com.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.ProductSearchRequest;
import com.shop.catalog.internal.dto.request.ProductSortField;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductPageResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.entity.ProductStatus;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.catalog.internal.storefront.dto.request.StorefrontProductSearchRequest;
import com.shop.catalog.internal.storefront.dto.response.StorefrontProductResponse;
import com.shop.catalog.internal.storefront.service.StorefrontCatalogService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogMySqlSearchIntegrationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_search_test")
            .withUsername("shop_search_test")
            .withPassword("shop-search-test-password");

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    @Autowired
    private StorefrontCatalogService storefrontService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
        registry.add("app.catalog.search.strategy", () -> "mysql-fulltext");
    }

    @Test
    void searchesCommittedProductsByFullTextSkuCategoryAndStatus() {
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_SEARCH")
                .name("MySQL Search")
                .slug("mysql-search")
                .build());
        ProductResponse keyboard = createProduct(category, "Wireless Mechanical Keyboard", "wireless-keyboard");
        keyboard = productService.addVariant(
                keyboard.getId(),
                CreateProductVariantRequest.builder()
                        .sku("KEYBOARD-WIRELESS-001")
                        .name("Black")
                        .price(new BigDecimal("89.90"))
                        .currency("USD")
                        .productVersion(keyboard.getVersion())
                        .build());
        productService.publish(
                keyboard.getId(),
                VersionedCatalogRequest.builder().version(keyboard.getVersion()).build());
        createProduct(category, "Minimal Desk Lamp", "minimal-desk-lamp");

        assertSingleResult("mechanical", category, null, "Wireless Mechanical Keyboard");
        assertSingleResult("keyboard-wireless-001", category, null, "Wireless Mechanical Keyboard");
        assertSingleResult("wireless", category, ProductStatus.PUBLISHED, "Wireless Mechanical Keyboard");

        ProductPageResponse draftOnly = search("wireless", category, ProductStatus.DRAFT);
        ProductPageResponse punctuation = search("%_!", category, null);
        var storefrontPage = storefrontService.search(StorefrontProductSearchRequest.builder()
                .categoryId(category.getId())
                .sortBy(ProductSortField.NAME)
                .direction(CatalogSortDirection.ASC)
                .page(0)
                .size(20)
                .build());
        assertThat(draftOnly.getTotalElements()).isZero();
        assertThat(punctuation.getTotalElements()).isZero();
        assertThat(storefrontPage.getTotalElements()).isEqualTo(1);
        assertThat(storefrontPage.getContent())
                .extracting(StorefrontProductResponse::getName)
                .containsExactly("Wireless Mechanical Keyboard");
    }

    @Test
    void createsFullTextIndexAndUsesIndexedFullTextAndSkuPlans() {
        Map<String, Object> fullTextPlan = jdbcTemplate.queryForMap("""
                EXPLAIN
                SELECT p.id
                  FROM san_pham_san_pham p
                 WHERE MATCH(p.name, p.slug) AGAINST ('+mechanical*' IN BOOLEAN MODE)
                """);
        Map<String, Object> skuPlan = jdbcTemplate.queryForMap("""
                EXPLAIN
                SELECT v.product_id
                  FROM san_pham_bien_the v
                 WHERE v.sku LIKE 'KEYBOARD-WIRELESS%' ESCAPE '!'
                """);

        assertThat(fullTextPlan)
                .containsEntry("type", "fulltext")
                .containsEntry("key", "ft_san_pham_san_pham_tim_kiem");
        assertThat(skuPlan).containsEntry("type", "range").containsEntry("key", "uk_san_pham_bien_the_sku");
    }

    private ProductResponse createProduct(CategoryResponse category, String name, String slug) {
        return productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name(name)
                .slug(slug)
                .build());
    }

    private void assertSingleResult(
            String keyword, CategoryResponse category, ProductStatus status, String expectedName) {
        ProductPageResponse response = search(keyword, category, status);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent()).extracting(ProductResponse::getName).containsExactly(expectedName);
    }

    private ProductPageResponse search(String keyword, CategoryResponse category, ProductStatus status) {
        return productService.search(ProductSearchRequest.builder()
                .keyword(keyword)
                .categoryId(category.getId())
                .status(status)
                .sortBy(ProductSortField.NAME)
                .direction(CatalogSortDirection.ASC)
                .page(0)
                .size(20)
                .build());
    }
}
