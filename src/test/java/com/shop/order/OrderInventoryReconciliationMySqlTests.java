package com.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.service.StockInventoryService;
import com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus;
import com.shop.order.internal.checkout.orchestration.recovery.OrderInventoryReconciliationProcessor;
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationQueryService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.service.CartService;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
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
class OrderInventoryReconciliationMySqlTests {

    private static final String SKU = "MYSQL-RECONCILE-001";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_reconciliation_test")
            .withUsername("shop_reconciliation_test")
            .withPassword("shop-reconciliation-test-password");

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    @Autowired
    private StockInventoryService stockInventoryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private OrderInventoryOrchestrationQueryService orchestrationQueryService;

    @Autowired
    private OrderInventoryReconciliationProcessor reconciliationProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
    }

    @Test
    void replaysACommittedReservationWithoutDuplicatingTheMovementOnMySql() {
        createPublishedProductAndStock();
        var cart = cartService.addItem(
                "mysql-reconciliation-owner",
                AddCartItemRequest.builder().sku(SKU).quantity(2).build());
        var orderId = orderCreationService.createFromCart("mysql-reconciliation-owner", cart.getVersion());
        var original = orchestrationQueryService.getByOrderId(orderId);
        int orchestrationUpdates = jdbcTemplate.update(
                "UPDATE don_hang_dieu_phoi_ton_kho SET status = 'PROCESSING', failure_code = NULL, "
                        + "updated_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 60 SECOND) "
                        + "WHERE request_event_id = ?",
                toBytes(original.requestEventId()));
        int lineUpdates = jdbcTemplate.update(
                "UPDATE don_hang_dong_giu_ton_kho SET status = 'PENDING', stock_item_id = NULL, "
                        + "failure_code = NULL, updated_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 60 SECOND) "
                        + "WHERE reservation_id = ?",
                toBytes(original.lines().getFirst().reservationId()));
        assertThat(orchestrationUpdates).isEqualTo(1);
        assertThat(lineUpdates).isEqualTo(1);

        var result = reconciliationProcessor.reconcileBatch(Instant.now());

        assertThat(result.recovered()).isEqualTo(1);
        assertThat(orchestrationQueryService.getByOrderId(orderId).status())
                .isEqualTo(InventoryOrchestrationStatus.RESERVED);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ton_kho_bien_dong "
                                + "WHERE reference_id = ? AND movement_type = 'RESERVATION'",
                        Integer.class,
                        original.lines().getFirst().reservationId().toString()))
                .isEqualTo(1);
    }

    private void createPublishedProductAndStock() {
        var category = categoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_RECONCILE")
                .name("MySQL Reconciliation")
                .slug("mysql-reconciliation")
                .build());
        var product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Reconciliation Product")
                .slug("mysql-reconciliation-product")
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku(SKU)
                        .name("Default")
                        .price(new BigDecimal("29.90"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        stockInventoryService.create(CreateStockItemRequest.builder()
                .sku(SKU)
                .locationCode("MAIN")
                .initialQuantity(10L)
                .reason("Tồn đầu kỳ cho MySQL reconciliation")
                .referenceId("MYSQL-RECONCILIATION-SETUP")
                .build());
    }

    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
