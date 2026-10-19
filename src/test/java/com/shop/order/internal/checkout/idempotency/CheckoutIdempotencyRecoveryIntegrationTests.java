package com.shop.order.internal.checkout.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.order.internal.checkout.dto.request.CheckoutOrderRequest;
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.service.CartService;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutIdempotencyRecoveryIntegrationTests {

    private static final String OWNER = "idempotency-recovery";
    private static final String KEY = "idempotency-recovery-key-001";
    private static final String SKU = "IDEMP-RECOVERY-01";

    @Autowired
    private CheckoutIdempotencyStateService stateService;

    @Autowired
    private CheckoutIdempotencyHasher hasher;

    @Autowired
    private CheckoutIdempotencyService idempotencyService;

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private CartService cartService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanCommittedData() {
        jdbcTemplate.update("DELETE FROM don_hang_yeu_cau_luy_dang WHERE owner_subject LIKE ?", OWNER + "%");
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang WHERE owner_subject LIKE ?", OWNER + "%");
        jdbcTemplate.update("""
                DELETE FROM don_hang_muc_gio_hang
                 WHERE cart_id IN (SELECT id FROM don_hang_gio_hang WHERE owner_subject LIKE ?)
                """, OWNER + "%");
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang WHERE owner_subject LIKE ?", OWNER + "%");
        jdbcTemplate.update("""
                DELETE FROM ton_kho_yeu_cau_luy_dang
                 WHERE result_stock_item_id IN (SELECT id FROM ton_kho_mat_hang WHERE sku = ?)
                """, SKU);
        jdbcTemplate.update("""
                DELETE FROM ton_kho_giu_hang
                 WHERE stock_item_id IN (SELECT id FROM ton_kho_mat_hang WHERE sku = ?)
                """, SKU);
        jdbcTemplate.update("""
                DELETE FROM ton_kho_bien_dong
                 WHERE stock_item_id IN (SELECT id FROM ton_kho_mat_hang WHERE sku = ?)
                """, SKU);
        jdbcTemplate.update("DELETE FROM ton_kho_mat_hang WHERE sku = ?", SKU);
        jdbcTemplate.update("DELETE FROM san_pham_bien_the WHERE sku = ?", SKU);
        jdbcTemplate.update("DELETE FROM san_pham_san_pham WHERE slug = 'idemp-recovery-product'");
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code = 'IDEMP_RECOVERY'");
    }

    @Test
    void recoversTheCommittedOrderWhenTheOriginalResponseWasInterruptedBeforeCompletionWasStored() {
        createPublishedItemAndStock();
        CartResponse cart = cartService.addItem(
                OWNER, AddCartItemRequest.builder().sku(SKU).quantity(2).build());
        CheckoutOrderRequest request = CheckoutOrderRequest.builder()
                .expectedCartVersion(cart.getVersion())
                .build();
        CheckoutIdempotencyClaim firstClaim = stateService.claim(OWNER, hasher.key(KEY), hasher.request(request));
        UUID committedOrderId =
                orderCreationService.createFromCart(OWNER, request.getExpectedCartVersion(), firstClaim.executionId());

        UUID replayedOrderId = idempotencyService.checkout(OWNER, KEY, request).getId();

        assertThat(replayedOrderId).isEqualTo(committedOrderId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM don_hang_yeu_cau_luy_dang WHERE owner_subject = ?", String.class, OWNER))
                .isEqualTo(CheckoutIdempotencyStatus.COMPLETED.name());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_don_dat_hang WHERE owner_subject = ?", Integer.class, OWNER))
                .isEqualTo(1);
    }

    @Test
    void purgesOnlyExpiredTerminalRecordsAndKeepsProcessingEvidenceForReconciliation() {
        CheckoutOrderRequest request =
                CheckoutOrderRequest.builder().expectedCartVersion(0L).build();
        String terminalOwner = OWNER + "-terminal";
        CheckoutIdempotencyClaim terminal =
                stateService.claim(terminalOwner, hasher.key(KEY + "-terminal"), hasher.request(request));
        stateService.fail(terminal.executionId(), com.shop.shared.error.ErrorCode.CHECKOUT_CART_EMPTY);
        String processingOwner = OWNER + "-processing";
        String processingKeyHash = hasher.key(KEY + "-processing");
        stateService.claim(processingOwner, processingKeyHash, hasher.request(request));
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        jdbcTemplate.update("""
                UPDATE don_hang_yeu_cau_luy_dang
                   SET created_at = ?, expires_at = ?
                 WHERE owner_subject IN (?, ?)
                """, Timestamp.from(twoDaysAgo), Timestamp.from(oneDayAgo), terminalOwner, processingOwner);

        CheckoutIdempotencyClaim processingRetry =
                stateService.claim(processingOwner, processingKeyHash, hasher.request(request));

        int deleted = stateService.purgeExpiredTerminal(Instant.now());

        assertThat(processingRetry.action()).isEqualTo(CheckoutIdempotencyClaimAction.IN_PROGRESS);
        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_yeu_cau_luy_dang WHERE owner_subject = ?",
                        Integer.class,
                        terminalOwner))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_yeu_cau_luy_dang WHERE owner_subject = ?",
                        Integer.class,
                        processingOwner))
                .isEqualTo(1);
    }

    private void createPublishedItemAndStock() {
        byte[] categoryId = uuidBytes(UUID.randomUUID());
        byte[] productId = uuidBytes(UUID.randomUUID());
        byte[] variantId = uuidBytes(UUID.randomUUID());
        byte[] stockItemId = uuidBytes(UUID.randomUUID());
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO san_pham_danh_muc
                    (id, code, name, slug, status, version, created_at, updated_at)
                VALUES (?, 'IDEMP_RECOVERY', 'Idempotency recovery', 'idemp-recovery',
                        'ACTIVE', 0, ?, ?)
                """, categoryId, now, now);
        jdbcTemplate.update("""
                INSERT INTO san_pham_san_pham
                    (id, category_id, name, slug, status, version, created_at, updated_at)
                VALUES (?, ?, 'Idempotency recovery product', 'idemp-recovery-product',
                        'PUBLISHED', 0, ?, ?)
                """, productId, categoryId, now, now);
        jdbcTemplate.update("""
                INSERT INTO san_pham_bien_the
                    (id, product_id, sku, name, price, currency, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'Recovery variant', 21.00, 'USD', 'ACTIVE', 0, ?, ?)
                """, variantId, productId, SKU, now, now);
        jdbcTemplate.update("""
                INSERT INTO ton_kho_mat_hang
                    (id, product_variant_id, sku, location_code, on_hand, reserved_quantity,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, 'MAIN', 10, 0, 0, ?, ?)
                """, stockItemId, variantId, SKU, now, now);
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
