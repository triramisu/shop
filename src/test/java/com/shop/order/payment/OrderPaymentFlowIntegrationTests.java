package com.shop.order.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.service.StockInventoryService;
import com.shop.order.event.OrderStatus;
import com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus;
import com.shop.order.internal.checkout.orchestration.InventoryReservationLineStatus;
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationQueryService;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.payment.service.OrderPaymentEventCoordinator;
import com.shop.order.internal.service.CartService;
import com.shop.payment.event.PaymentStatus;
import com.shop.payment.event.PaymentStatusChangedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest(
        properties = {
            "app.order.payment.auto-initiation-enabled=true",
            "app.order.payment.event-consumption-enabled=true",
            "app.order.payment.recovery.enabled=false",
            "app.payment.fake.default-mode=pending"
        })
@ActiveProfiles("test")
@RecordApplicationEvents
class OrderPaymentFlowIntegrationTests {

    private static final String OWNER = "payment-flow-owner";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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
    private OrderSnapshotQueryService orderSnapshotQueryService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderPaymentEventCoordinator paymentEventCoordinator;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearEvents() {
        applicationEvents.clear();
    }

    @AfterEach
    void cleanCommittedData() {
        jdbcTemplate.update(
                "DELETE FROM don_hang_su_kien_thanh_toan WHERE order_id IN "
                        + "(SELECT id FROM don_hang_don_dat_hang WHERE owner_subject = ?)",
                OWNER);
        jdbcTemplate.update(
                "DELETE FROM thanh_toan_lan_thu WHERE order_id IN "
                        + "(SELECT id FROM don_hang_don_dat_hang WHERE owner_subject = ?)",
                OWNER);
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang WHERE owner_subject = ?", OWNER);
        jdbcTemplate.update(
                "DELETE FROM don_hang_muc_gio_hang WHERE cart_id IN "
                        + "(SELECT id FROM don_hang_gio_hang WHERE owner_subject = ?)",
                OWNER);
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang WHERE owner_subject = ?", OWNER);
        jdbcTemplate.update("DELETE FROM ton_kho_yeu_cau_luy_dang WHERE result_stock_item_id IN "
                + "(SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'PAY-FLOW-%')");
        jdbcTemplate.update("DELETE FROM ton_kho_giu_hang WHERE stock_item_id IN "
                + "(SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'PAY-FLOW-%')");
        jdbcTemplate.update("DELETE FROM ton_kho_bien_dong WHERE stock_item_id IN "
                + "(SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'PAY-FLOW-%')");
        jdbcTemplate.update("DELETE FROM ton_kho_mat_hang WHERE sku LIKE 'PAY-FLOW-%'");
        jdbcTemplate.update("DELETE FROM san_pham_bien_the WHERE sku LIKE 'PAY-FLOW-%'");
        jdbcTemplate.update("DELETE FROM san_pham_san_pham WHERE slug LIKE 'pay-flow-%'");
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code LIKE 'PAY_FLOW_%'");
    }

    @Test
    void confirmsInventoryAndPaysOrderExactlyOnceAfterPaymentSuccess() {
        Flow flow = createPendingPaymentFlow(2);
        PaymentStatusChangedEvent success = terminalEvent(flow.pendingEvent(), PaymentStatus.SUCCEEDED, null, 1);

        eventPublisher.publishEvent(success);
        eventPublisher.publishEvent(success);

        assertThat(orderSnapshotQueryService
                        .getOwnedOrder(OWNER, flow.orderId())
                        .getStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(orchestrationQueryService.getByOrderId(flow.orderId())).satisfies(orchestration -> {
            assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.PAYMENT_CONFIRMED);
            assertThat(orchestration.lastPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(orchestration.lines()).singleElement().satisfies(line -> assertThat(line.status())
                    .isEqualTo(InventoryReservationLineStatus.CONFIRMED));
        });
        assertThat(stockInventoryService.getById(flow.stock().getId())).satisfies(stock -> {
            assertThat(stock.getOnHand()).isEqualTo(8);
            assertThat(stock.getReserved()).isZero();
        });
        assertThat(movementCount(flow.stock().getId(), "CONFIRMATION")).isEqualTo(1);
        assertThat(inboxCount(success.eventId())).isEqualTo(1);
        assertThat(inboxOutcome(success.eventId())).isEqualTo("COMPLETED");
    }

    @Test
    void releasesInventoryAndCancelsOrderAfterPaymentFailure() {
        Flow flow = createPendingPaymentFlow(2);
        PaymentStatusChangedEvent failure =
                terminalEvent(flow.pendingEvent(), PaymentStatus.FAILED, "PAYMENT_DECLINED", 1);

        eventPublisher.publishEvent(failure);
        eventPublisher.publishEvent(failure);

        assertThat(orderSnapshotQueryService
                        .getOwnedOrder(OWNER, flow.orderId())
                        .getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(orchestrationQueryService.getByOrderId(flow.orderId())).satisfies(orchestration -> {
            assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.PAYMENT_RELEASED);
            assertThat(orchestration.lastPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(orchestration.lines()).singleElement().satisfies(line -> assertThat(line.status())
                    .isEqualTo(InventoryReservationLineStatus.RELEASED));
        });
        assertThat(stockInventoryService.getById(flow.stock().getId())).satisfies(stock -> {
            assertThat(stock.getOnHand()).isEqualTo(10);
            assertThat(stock.getReserved()).isZero();
        });
        assertThat(movementCount(flow.stock().getId(), "RELEASE")).isEqualTo(1);
        assertThat(inboxCount(failure.eventId())).isEqualTo(1);
    }

    @Test
    void ignoresAnOlderFailureAfterThePaymentWasAlreadyApplied() {
        Flow flow = createPendingPaymentFlow(1);
        PaymentStatusChangedEvent success = terminalEvent(flow.pendingEvent(), PaymentStatus.SUCCEEDED, null, 2);
        eventPublisher.publishEvent(success);
        PaymentStatusChangedEvent staleFailure =
                terminalEvent(flow.pendingEvent(), PaymentStatus.FAILED, "STALE_DECLINE", 1);

        eventPublisher.publishEvent(staleFailure);

        assertThat(orderSnapshotQueryService
                        .getOwnedOrder(OWNER, flow.orderId())
                        .getStatus())
                .isEqualTo(OrderStatus.PAID);
        assertThat(movementCount(flow.stock().getId(), "CONFIRMATION")).isEqualTo(1);
        assertThat(movementCount(flow.stock().getId(), "RELEASE")).isZero();
        assertThat(inboxOutcome(staleFailure.eventId())).isEqualTo("IGNORED");
    }

    @Test
    void requiresManualActionWhenInventoryExpiredBeforeAConfirmedPayment() {
        Flow flow = createPendingPaymentFlow(1);
        Instant createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM ton_kho_giu_hang WHERE id = ?", Instant.class, flow.reservationId());
        jdbcTemplate.update(
                "UPDATE ton_kho_giu_hang SET expires_at = ? WHERE id = ?",
                createdAt.plusNanos(1_000),
                flow.reservationId());
        PaymentStatusChangedEvent success = terminalEvent(flow.pendingEvent(), PaymentStatus.SUCCEEDED, null, 1);

        eventPublisher.publishEvent(success);

        assertThat(orderSnapshotQueryService
                        .getOwnedOrder(OWNER, flow.orderId())
                        .getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(orchestrationQueryService.getByOrderId(flow.orderId()).status())
                .isEqualTo(InventoryOrchestrationStatus.PAYMENT_RECOVERY_REQUIRED);
        assertThat(stockInventoryService.getById(flow.stock().getId())).satisfies(stock -> {
            assertThat(stock.getOnHand()).isEqualTo(10);
            assertThat(stock.getReserved()).isZero();
        });
        assertThat(inboxOutcome(success.eventId())).isEqualTo("MANUAL_ACTION_REQUIRED");
    }

    @Test
    void rejectsTheSameEventIdWhenItsPayloadChanges() {
        Flow flow = createPendingPaymentFlow(1);
        PaymentStatusChangedEvent success = terminalEvent(flow.pendingEvent(), PaymentStatus.SUCCEEDED, null, 1);
        eventPublisher.publishEvent(success);
        PaymentStatusChangedEvent conflicting = new PaymentStatusChangedEvent(
                success.eventVersion(),
                success.eventId(),
                success.paymentAttemptId(),
                success.orderId(),
                PaymentStatus.PENDING,
                PaymentStatus.FAILED,
                success.amount(),
                success.currency(),
                success.providerCode(),
                success.providerReference(),
                "CONFLICTING_PAYLOAD",
                success.occurredAt());

        assertThatThrownBy(() -> paymentEventCoordinator.handle(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different payload");
        assertThat(inboxCount(success.eventId())).isEqualTo(1);
    }

    private Flow createPendingPaymentFlow(int quantity) {
        int sequence = SEQUENCE.incrementAndGet();
        String suffix = Integer.toString(sequence);
        String sku = "PAY-FLOW-" + suffix;
        createPublishedProduct(suffix, sku);
        StockItemResponse stock = createStock(sku, 10);
        CartResponse cart = cartService.addItem(
                OWNER, AddCartItemRequest.builder().sku(sku).quantity(quantity).build());

        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());
        var orchestration = orchestrationQueryService.getByOrderId(orderId);
        PaymentStatusChangedEvent pendingEvent = applicationEvents.stream(PaymentStatusChangedEvent.class)
                .filter(event -> event.orderId().equals(orderId) && event.currentStatus() == PaymentStatus.PENDING)
                .findFirst()
                .orElseThrow();

        assertThat(orchestration.status()).isEqualTo(InventoryOrchestrationStatus.PAYMENT_PENDING);
        assertThat(orchestration.paymentAttemptId()).isEqualTo(pendingEvent.paymentAttemptId());
        assertThat(stockInventoryService.getById(stock.getId()).getReserved()).isEqualTo(quantity);
        return new Flow(orderId, stock, orchestration.lines().getFirst().reservationId(), pendingEvent);
    }

    private ProductResponse createPublishedProduct(String suffix, String sku) {
        var category = categoryService.create(CreateCategoryRequest.builder()
                .code("PAY_FLOW_" + suffix)
                .name("Payment flow " + suffix)
                .slug("pay-flow-category-" + suffix)
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Payment flow product " + suffix)
                .slug("pay-flow-product-" + suffix)
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku(sku)
                        .name("Payment flow variant " + suffix)
                        .price(new BigDecimal("25.00"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        return productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
    }

    private StockItemResponse createStock(String sku, long initialQuantity) {
        return stockInventoryService.create(CreateStockItemRequest.builder()
                .sku(sku)
                .locationCode("MAIN")
                .initialQuantity(initialQuantity)
                .reason("Tồn đầu kỳ cho luồng payment")
                .referenceId("PAYMENT-FLOW-SETUP")
                .build());
    }

    private PaymentStatusChangedEvent terminalEvent(
            PaymentStatusChangedEvent pendingEvent,
            PaymentStatus targetStatus,
            String failureCode,
            long occurredAfterSeconds) {
        return new PaymentStatusChangedEvent(
                PaymentStatusChangedEvent.CURRENT_VERSION,
                UUID.randomUUID(),
                pendingEvent.paymentAttemptId(),
                pendingEvent.orderId(),
                PaymentStatus.PENDING,
                targetStatus,
                pendingEvent.amount(),
                pendingEvent.currency(),
                pendingEvent.providerCode(),
                pendingEvent.providerReference(),
                failureCode,
                pendingEvent.occurredAt().plusSeconds(occurredAfterSeconds));
    }

    private int movementCount(UUID stockItemId, String movementType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id = ? AND movement_type = ?",
                Integer.class,
                stockItemId,
                movementType);
    }

    private int inboxCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM don_hang_su_kien_thanh_toan WHERE event_id = ?", Integer.class, eventId);
    }

    private String inboxOutcome(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT outcome FROM don_hang_su_kien_thanh_toan WHERE event_id = ?", String.class, eventId);
    }

    private record Flow(
            UUID orderId, StockItemResponse stock, UUID reservationId, PaymentStatusChangedEvent pendingEvent) {}
}
