package com.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.service.CartService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class CartMySqlConcurrencyTests {

    private static final int WORKERS = 24;
    private static final int TIMEOUT_SECONDS = 30;

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_cart_test")
            .withUsername("shop_cart_test")
            .withPassword("shop-cart-test-password");

    @Autowired
    private CartService cartService;

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Test
    void concurrentFirstAccessCreatesExactlyOneCartForTheOwner() throws Exception {
        String owner = "mysql-cart-first-access";
        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        List<CartResponse> responses = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(WORKERS)) {
            List<Future<CartResponse>> futures = new ArrayList<>();
            for (int index = 0; index < WORKERS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                    return cartService.getCart(owner);
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CartResponse> future : futures) {
                responses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        }

        assertThat(responses).hasSize(WORKERS).allSatisfy(response -> assertThat(response.getItems())
                .isEmpty());
        assertThat(responses.stream().map(CartResponse::getId).collect(Collectors.toSet()))
                .hasSize(1);
    }

    @Test
    void concurrentAddsOfTheSameSkuNeverLoseQuantityOrCreateDuplicateItems() throws Exception {
        String owner = "mysql-cart-owner";
        String sku = createPublishedSku();
        cartService.getCart(owner);
        AddCartItemRequest request =
                AddCartItemRequest.builder().sku(sku).quantity(1).build();
        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(WORKERS)) {
            List<Future<CartResponse>> futures = new ArrayList<>();
            for (int index = 0; index < WORKERS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                    return cartService.addItem(owner, request);
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CartResponse> future : futures) {
                assertThat(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
            }
        }

        CartResponse cart = cartService.getCart(owner);
        assertThat(cart.getDistinctItemCount()).isEqualTo(1);
        assertThat(cart.getTotalQuantity()).isEqualTo(WORKERS);
        assertThat(cart.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSku()).isEqualTo(sku);
            assertThat(item.getQuantity()).isEqualTo(WORKERS);
        });
    }

    private String createPublishedSku() {
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_CART")
                .name("MySQL Cart")
                .slug("mysql-cart")
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Cart Product")
                .slug("mysql-cart-product")
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku("MYSQL-CART-001")
                        .name("Default")
                        .price(new BigDecimal("19.90"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        return "MYSQL-CART-001";
    }
}
