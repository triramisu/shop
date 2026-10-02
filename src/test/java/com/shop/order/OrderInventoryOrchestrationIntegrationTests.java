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
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.service.StockInventoryService;
import com.shop.order.event.OrderInventoryCompensationEvent;
import com.shop.order.event.OrderInventoryCompensationStatus;
import com.shop.order.event.OrderInventoryReservationFailedEvent;
import com.shop.order.event.OrderInventoryReservedEvent;
import com.shop.order.event.OrderStatus;
import com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus;
import com.shop.order.internal.checkout.orchestration.InventoryReservationLineStatus;
import com.shop.order.internal.checkout.orchestration.OrderInventoryReservationCoordinator;
import com.shop.order.internal.checkout.orchestration.recovery.OrderInventoryReconciliationProcessor;
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.checkout.service.OrderInventoryFailureInjectionFixture;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationQueryService;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.service.CartService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class OrderInventoryOrchestrationIntegrationTests {

    private static final String OWNER = "orchestration-owner";

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
    private OrderInventoryReservationCoordinator coordinator;

    @Autowired
    private OrderSnapshotQueryService orderSnapshotQueryService;

    @Autowired
    private OrderInventoryReconciliationProcessor reconciliationProcessor;

    @Autowired
    private OrderInventoryFailureInjectionFixture failureInjectionFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void clearEvents() {
        applicationEvents.clear();
    }

    @AfterEach
    void cleanCommittedData() {
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang WHERE owner_subject = ?", OWNER);
        jdbcTemplate.update(
                "DELETE FROM don_hang_muc_gio_hang WHERE cart_id IN "
                        + "(SELECT id FROM don_hang_gio_hang WHERE owner_subject = ?)",
                OWNER);
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang WHERE owner_subject = ?", OWNER);
        jdbcTemplate.update("DELETE FROM ton_kho_yeu_cau_luy_dang WHERE result_stock_item_id IN "
                + "(SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'ORCH-%')");
        jdbcTemplate.update("DELETE FROM ton_kho_giu_hang WHERE stock_item_id IN "
                + "(SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'ORCH-%')");
        jdbcTemplate.update("DELETE FROM ton_kho_bien_dong WHERE stock_item_id IN "
                + "(SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'ORCH-%')");
        jdbcTemplate.update("DELETE FROM ton_kho_mat_hang WHERE sku LIKE 'ORCH-%'");
        jdbcTemplate.update("DELETE FROM san_pham_bien_the WHERE sku LIKE 'ORCH-%'");
        jdbcTemplate.update("DELETE FROM san_pham_san_pham WHERE slug LIKE 'orch-%'");
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code LIKE 'ORCH_%'");
    }

    @Test
    void reservesEveryLineAfterCommitAndIgnoresADuplicateBusinessEvent() {
        ProductResponse product = createPublishedProduct("SUCCESS", List.of("ORCH-SUCCESS-01"));
        StockItemResponse stock = createStock("ORCH-SUCCESS-01", 10);
        CartResponse cart = addToCart("ORCH-SUCCESS-01", 2);

        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());

        var orchestration = orchestrationQueryService.getByOrderId(orderId);
        assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.RESERVED);
        assertThat(orchestration.correlationId()).isNotNull();
        assertThat(orchestration.lines()).singleElement().satisfies(line -> {
            assertThat(line.sku()).isEqualTo("ORCH-SUCCESS-01");
            assertThat(line.stockItemId()).isEqualTo(stock.getId());
            assertThat(line.status()).isEqualTo(InventoryReservationLineStatus.RESERVED);
        });
        assertThat(orderSnapshotQueryService.getOwnedOrder(OWNER, orderId).getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(stockInventoryService.getById(stock.getId()).getReserved()).isEqualTo(2);
        assertThat(applicationEvents.stream(OrderInventoryReservedEvent.class)).hasSize(1);

        coordinator.handle(orchestrationQueryService.getRequestedEvent(orderId));

        assertThat(stockInventoryService.getById(stock.getId()).getReserved()).isEqualTo(2);
        assertThat(reservationMovementCount(stock.getId())).isEqualTo(1);
        assertThat(applicationEvents.stream(OrderInventoryReservedEvent.class)).hasSize(1);
        assertThat(product.getVariants()).hasSize(1);
    }

    @Test
    void releasesAnEarlierReservationWhenALaterLineHasInsufficientStock() {
        createPublishedProduct("PARTIAL", List.of("ORCH-PARTIAL-01", "ORCH-PARTIAL-02"));
        StockItemResponse firstStock = createStock("ORCH-PARTIAL-01", 10);
        StockItemResponse secondStock = createStock("ORCH-PARTIAL-02", 1);
        addToCart("ORCH-PARTIAL-01", 2);
        CartResponse cart = addToCart("ORCH-PARTIAL-02", 2);

        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());

        var orchestration = orchestrationQueryService.getByOrderId(orderId);
        assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.FAILED);
        assertThat(orchestration.failureCode()).isEqualTo("INVENTORY_INSUFFICIENT_STOCK");
        assertThat(orchestration.lines())
                .extracting(line -> line.status())
                .containsExactly(InventoryReservationLineStatus.RELEASED, InventoryReservationLineStatus.FAILED);
        assertThat(stockInventoryService.getById(firstStock.getId()).getReserved())
                .isZero();
        assertThat(stockInventoryService.getById(secondStock.getId()).getReserved())
                .isZero();
        assertThat(orderSnapshotQueryService.getOwnedOrder(OWNER, orderId).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT movement_type FROM ton_kho_bien_dong "
                                + "WHERE stock_item_id = ? ORDER BY occurred_at, movement_type",
                        String.class,
                        firstStock.getId()))
                .contains("INITIAL", "RESERVATION", "RELEASE");
        assertThat(applicationEvents.stream(OrderInventoryReservationFailedEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.retryable()).isFalse());
        assertThat(applicationEvents.stream(OrderInventoryCompensationEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.status()).isEqualTo(OrderInventoryCompensationStatus.COMPLETED));
    }

    @Test
    void cancelsTheOrderWithoutTouchingInventoryWhenNoStockTargetExists() {
        createPublishedProduct("MISSING", List.of("ORCH-MISSING-01"));
        CartResponse cart = addToCart("ORCH-MISSING-01", 1);

        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());

        var orchestration = orchestrationQueryService.getByOrderId(orderId);
        assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.FAILED);
        assertThat(orchestration.failureCode()).isEqualTo("STOCK_ITEM_NOT_FOUND");
        assertThat(orchestration.lines()).singleElement().satisfies(line -> {
            assertThat(line.stockItemId()).isNull();
            assertThat(line.status()).isEqualTo(InventoryReservationLineStatus.FAILED);
        });
        assertThat(orderSnapshotQueryService.getOwnedOrder(OWNER, orderId).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void recoversAReservationRequestWhoseAfterCommitEventWasLost() {
        ProductResponse product = createPublishedProduct("LOST", List.of("ORCH-LOST-01"));
        StockItemResponse stock = createStock("ORCH-LOST-01", 10);
        UUID orderId = createOrderWithoutPublishingEvent(
                product.getVariants().getFirst().getId(), "ORCH-LOST-01", 2);

        var result = reconciliationProcessor.reconcileBatch(Instant.now());

        assertThat(result.selected()).isEqualTo(1);
        assertThat(result.recovered()).isEqualTo(1);
        assertThat(orchestrationQueryService.getByOrderId(orderId).status())
                .isEqualTo(InventoryOrchestrationStatus.RESERVED);
        assertThat(stockInventoryService.getById(stock.getId()).getReserved()).isEqualTo(2);
        assertThat(reservationMovementCount(stock.getId())).isEqualTo(1);
    }

    @Test
    void replaysAnInventoryCommitAfterTheOrderProcessStopsBeforeRecordingIt() {
        createPublishedProduct("REPLAY", List.of("ORCH-REPLAY-01"));
        StockItemResponse stock = createStock("ORCH-REPLAY-01", 10);
        CartResponse cart = addToCart("ORCH-REPLAY-01", 2);
        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());
        var original = orchestrationQueryService.getByOrderId(orderId);
        Instant staleAt = Instant.now().minusSeconds(60);
        jdbcTemplate.update(
                "UPDATE don_hang_dieu_phoi_ton_kho SET status = 'PROCESSING', failure_code = NULL, updated_at = ? "
                        + "WHERE request_event_id = ?",
                staleAt,
                original.requestEventId());
        jdbcTemplate.update(
                "UPDATE don_hang_dong_giu_ton_kho SET status = 'PENDING', stock_item_id = NULL, "
                        + "failure_code = NULL, updated_at = ? WHERE reservation_id = ?",
                staleAt,
                original.lines().getFirst().reservationId());

        var result = reconciliationProcessor.reconcileBatch(Instant.now());

        assertThat(result.recovered()).isEqualTo(1);
        assertThat(orchestrationQueryService.getByOrderId(orderId).status())
                .isEqualTo(InventoryOrchestrationStatus.RESERVED);
        assertThat(stockInventoryService.getById(stock.getId()).getReserved()).isEqualTo(2);
        assertThat(reservationMovementCount(stock.getId())).isEqualTo(1);
    }

    @Test
    void resumesCompensationWithoutReleasingStockTwice() {
        createPublishedProduct("RECOVER-COMP", List.of("ORCH-RECOVER-COMP-01"));
        StockItemResponse stock = createStock("ORCH-RECOVER-COMP-01", 10);
        CartResponse cart = addToCart("ORCH-RECOVER-COMP-01", 2);
        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());
        var original = orchestrationQueryService.getByOrderId(orderId);
        jdbcTemplate.update(
                "UPDATE don_hang_dieu_phoi_ton_kho SET status = 'COMPENSATION_REQUIRED', "
                        + "failure_code = 'INJECTED_AFTER_RESERVE', updated_at = ? WHERE request_event_id = ?",
                Instant.now().minusSeconds(60),
                original.requestEventId());

        var firstRun = reconciliationProcessor.reconcileBatch(Instant.now());
        var secondRun = reconciliationProcessor.reconcileBatch(Instant.now());

        assertThat(firstRun.recovered()).isEqualTo(1);
        assertThat(secondRun.selected()).isZero();
        assertThat(orchestrationQueryService.getByOrderId(orderId)).satisfies(orchestration -> {
            assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.FAILED);
            assertThat(orchestration.lines().getFirst().status()).isEqualTo(InventoryReservationLineStatus.RELEASED);
        });
        assertThat(orderSnapshotQueryService.getOwnedOrder(OWNER, orderId).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockInventoryService.getById(stock.getId()).getReserved()).isZero();
        assertThat(releaseMovementCount(stock.getId())).isEqualTo(1);
    }

    @Test
    void doesNotStealAFreshProcessingOrchestration() {
        createPublishedProduct("FRESH", List.of("ORCH-FRESH-01"));
        createStock("ORCH-FRESH-01", 10);
        CartResponse cart = addToCart("ORCH-FRESH-01", 1);
        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());
        var original = orchestrationQueryService.getByOrderId(orderId);
        jdbcTemplate.update(
                "UPDATE don_hang_dieu_phoi_ton_kho SET status = 'PROCESSING', updated_at = ? "
                        + "WHERE request_event_id = ?",
                Instant.now(),
                original.requestEventId());

        var result = reconciliationProcessor.reconcileBatch(Instant.now());

        assertThat(result.selected()).isZero();
        assertThat(orchestrationQueryService.getByOrderId(orderId).status())
                .isEqualTo(InventoryOrchestrationStatus.PROCESSING);
    }

    private ProductResponse createPublishedProduct(String suffix, List<String> skus) {
        String normalized = suffix.toLowerCase(java.util.Locale.ROOT);
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("ORCH_" + suffix)
                .name("Orchestration " + suffix)
                .slug("orch-" + normalized)
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Orchestration product " + suffix)
                .slug("orch-product-" + normalized)
                .build());
        for (String sku : skus) {
            product = productService.addVariant(
                    product.getId(),
                    CreateProductVariantRequest.builder()
                            .sku(sku)
                            .name(sku + " name")
                            .price(new BigDecimal("25.00"))
                            .currency("USD")
                            .productVersion(product.getVersion())
                            .build());
        }
        return productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
    }

    private StockItemResponse createStock(String sku, long initialQuantity) {
        return stockInventoryService.create(CreateStockItemRequest.builder()
                .sku(sku)
                .locationCode("MAIN")
                .initialQuantity(initialQuantity)
                .reason("Tồn đầu kỳ cho checkout")
                .referenceId("ORCH-SETUP")
                .build());
    }

    private CartResponse addToCart(String sku, int quantity) {
        return cartService.addItem(
                OWNER, AddCartItemRequest.builder().sku(sku).quantity(quantity).build());
    }

    private int reservationMovementCount(UUID stockItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ton_kho_bien_dong " + "WHERE stock_item_id = ? AND movement_type = 'RESERVATION'",
                Integer.class,
                stockItemId);
    }

    private int releaseMovementCount(UUID stockItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id = ? AND movement_type = 'RELEASE'",
                Integer.class,
                stockItemId);
    }

    private UUID createOrderWithoutPublishingEvent(UUID productVariantId, String sku, int quantity) {
        return failureInjectionFixture.createWithoutPublishingEvent(OWNER, productVariantId, sku, quantity);
    }
}
