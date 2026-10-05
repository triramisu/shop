package com.shop.identity;

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
import com.shop.catalog.internal.image.dto.request.UploadProductImagesRequest;
import com.shop.catalog.internal.image.dto.response.ProductImageResponse;
import com.shop.catalog.internal.image.service.ProductImageService;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import com.shop.identity.internal.dto.request.AuthenticationRequest;
import com.shop.identity.internal.dto.request.ChangePasswordRequest;
import com.shop.identity.internal.dto.request.IntrospectRequest;
import com.shop.identity.internal.dto.request.RefreshRequest;
import com.shop.identity.internal.dto.request.RegisterUserRequest;
import com.shop.identity.internal.dto.request.UpdateProfileRequest;
import com.shop.identity.internal.dto.response.AuthenticationResponse;
import com.shop.identity.internal.dto.response.IntrospectResponse;
import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.service.AuthenticationService;
import com.shop.identity.internal.service.RegistrationService;
import com.shop.identity.internal.service.UserProfileService;
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.dto.request.StockAdjustmentRequest;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.service.StockInventoryService;
import com.shop.order.internal.checkout.dto.request.CheckoutOrderRequest;
import com.shop.order.internal.checkout.dto.request.CheckoutQuoteRequest;
import com.shop.order.internal.checkout.dto.response.CheckoutQuoteResponse;
import com.shop.order.internal.checkout.dto.response.OrderSnapshotResponse;
import com.shop.order.internal.checkout.idempotency.CheckoutIdempotencyService;
import com.shop.order.internal.checkout.service.CheckoutPricingService;
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.checkout.service.OrderInventoryOrchestrationQueryService;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.service.CartService;
import com.shop.payment.event.PaymentStatus;
import com.shop.payment.internal.entity.PaymentAttempt;
import com.shop.payment.internal.repository.PaymentAttemptRepository;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
class MySqlCompatibilityIntegrationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.0.46"))
            .withDatabaseName("shop_test")
            .withUsername("shop_test")
            .withPassword("shop-test-password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdaptiveCaptchaService adaptiveCaptchaService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private CatalogCategoryService catalogCategoryService;

    @Autowired
    private CatalogProductService catalogProductService;

    @Autowired
    private ProductImageService productImageService;

    @Autowired
    private StockInventoryService stockInventoryService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CheckoutPricingService checkoutPricingService;

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private OrderInventoryOrchestrationQueryService orderInventoryOrchestrationQueryService;

    @Autowired
    private OrderSnapshotQueryService orderSnapshotQueryService;

    @Autowired
    private CheckoutIdempotencyService checkoutIdempotencyService;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
        registry.add("app.catalog.search.strategy", () -> "mysql-fulltext");
        registry.add("app.security.captcha.enabled", () -> "true");
    }

    @Test
    void appliesFlywayMigrationsAndValidatesTheMySqlSchema() {
        String databaseVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class);
        Integer defaultRoleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_vai_tro WHERE code IN ('ADMIN', 'STAFF', 'USER')", Integer.class);

        assertThat(databaseVersion).startsWith("8.0.");
        assertThat(migrationCount).isEqualTo(22);
        assertThat(defaultRoleCount).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("SELECT version FROM xac_thuc_vai_tro", Long.class))
                .containsOnly(0L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name LIKE 'san_pham_%'",
                        Integer.class))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name LIKE 'ton_kho_%'",
                        Integer.class))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name LIKE 'don_hang_%'",
                        Integer.class))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = 'thanh_toan_lan_thu'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void persistsPaymentLifecycleAndImmutableTermsOnMySql() {
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-03T00:00:00Z");
        PaymentAttempt attempt = PaymentAttempt.start(
                UUID.randomUUID(), orderId, 1, new BigDecimal("125000.00"), "vnd", "stripe", createdAt);
        URI actionUrl = URI.create("https://checkout.stripe.com/c/pay/mysql-session");
        attempt.transition(
                PaymentStatus.REQUIRES_ACTION, "mysql-payment-reference-1", actionUrl, null, createdAt.plusSeconds(1));

        PaymentAttempt saved = paymentAttemptRepository.saveAndFlush(attempt);
        PaymentAttempt reloaded =
                paymentAttemptRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getOrderId()).isEqualTo(orderId);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("125000.00");
        assertThat(reloaded.getCurrency()).isEqualTo("VND");
        assertThat(reloaded.getProviderCode()).isEqualTo("STRIPE");
        assertThat(reloaded.getProviderReference()).isEqualTo("mysql-payment-reference-1");
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.REQUIRES_ACTION);
        assertThat(reloaded.getActionUrl()).isEqualTo(actionUrl);

        reloaded.transition(PaymentStatus.SUCCEEDED, null, null, null, createdAt.plusSeconds(2));
        PaymentAttempt completed = paymentAttemptRepository.saveAndFlush(reloaded);
        assertThat(completed.getActionUrl()).isNull();
        assertThat(completed.getCompletedAt()).isEqualTo(createdAt.plusSeconds(2));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM thanh_toan_lan_thu WHERE order_id = UNHEX(REPLACE(?, '-', ''))",
                        Integer.class,
                        orderId.toString()))
                .isEqualTo(1);
    }

    @Test
    void persistsBalancedInventoryLedgerOnMySql() {
        CategoryResponse category = catalogCategoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_INVENTORY")
                .name("MySQL Inventory")
                .slug("mysql-inventory")
                .build());
        ProductResponse product = catalogProductService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Inventory Product")
                .slug("mysql-inventory-product")
                .build());
        catalogProductService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku("MYSQL-INVENTORY-01")
                        .name("Default")
                        .price(new java.math.BigDecimal("29.90"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());

        StockItemResponse created = stockInventoryService.create(CreateStockItemRequest.builder()
                .sku("MYSQL-INVENTORY-01")
                .locationCode("WAREHOUSE_MYSQL")
                .initialQuantity(12L)
                .reason("Tồn đầu kỳ")
                .referenceId("MYSQL-RECEIPT-001")
                .build());
        StockItemResponse adjusted = stockInventoryService.adjust(
                created.getId(),
                StockAdjustmentRequest.builder()
                        .quantityDelta(-2L)
                        .reason("Kiểm kê")
                        .referenceId("MYSQL-COUNT-001")
                        .version(created.getVersion())
                        .build());

        assertThat(adjusted.getOnHand()).isEqualTo(10);
        assertThat(adjusted.getReserved()).isZero();
        assertThat(adjusted.getAvailable()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id = UNHEX(REPLACE(?, '-', ''))",
                        Integer.class,
                        created.getId().toString()))
                .isEqualTo(2);
    }

    @Test
    void quotesTheCurrentCatalogPriceThroughThePublishedContractOnMySql() {
        CategoryResponse category = catalogCategoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_CHECKOUT")
                .name("MySQL Checkout")
                .slug("mysql-checkout")
                .build());
        ProductResponse product = catalogProductService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Checkout Product")
                .slug("mysql-checkout-product")
                .build());
        product = catalogProductService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku("MYSQL-CHECKOUT-01")
                        .name("Default")
                        .price(new java.math.BigDecimal("29.90"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        catalogProductService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        stockInventoryService.create(CreateStockItemRequest.builder()
                .sku("MYSQL-CHECKOUT-01")
                .locationCode("MAIN")
                .initialQuantity(5L)
                .reason("Tồn kho checkout MySQL")
                .build());

        String owner = "mysql-checkout-owner";
        cartService.getCart(owner);
        CartResponse cart = cartService.addItem(
                owner,
                AddCartItemRequest.builder()
                        .sku("MYSQL-CHECKOUT-01")
                        .quantity(2)
                        .build());
        CheckoutQuoteResponse quote = checkoutPricingService.quote(
                owner,
                CheckoutQuoteRequest.builder()
                        .expectedCartVersion(cart.getVersion())
                        .build());

        assertThat(quote.getCurrency()).isEqualTo("USD");
        assertThat(quote.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualTo(2);
            assertThat(line.getUnitPrice()).isEqualByComparingTo("29.90");
            assertThat(line.getTotal()).isEqualByComparingTo("59.80");
        });
        assertThat(quote.getGrandTotal()).isEqualByComparingTo("59.80");
        java.util.UUID orderId = orderCreationService.createFromCart(owner, cart.getVersion());
        assertThat(orderInventoryOrchestrationQueryService.getByOrderId(orderId).status())
                .isEqualTo(com.shop.order.internal.checkout.orchestration.InventoryOrchestrationStatus.RESERVED);
        assertThat(orderSnapshotQueryService.getOwnedOrder(owner, orderId).getItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSku()).isEqualTo("MYSQL-CHECKOUT-01");
                    assertThat(item.getQuantity()).isEqualTo(2);
                    assertThat(item.getUnitPrice()).isEqualByComparingTo("29.9000");
                    assertThat(item.getTotal()).isEqualByComparingTo("59.8000");
                });
    }

    @Test
    void serializesConcurrentCheckoutRequestsWithTheSameIdempotencyKeyOnMySql() throws Exception {
        CategoryResponse category = catalogCategoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_IDEMPOTENCY")
                .name("MySQL Idempotency")
                .slug("mysql-idempotency")
                .build());
        ProductResponse product = catalogProductService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Idempotency Product")
                .slug("mysql-idempotency-product")
                .build());
        product = catalogProductService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku("MYSQL-IDEMPOTENCY-01")
                        .name("Default")
                        .price(new java.math.BigDecimal("31.50"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        catalogProductService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        StockItemResponse stock = stockInventoryService.create(CreateStockItemRequest.builder()
                .sku("MYSQL-IDEMPOTENCY-01")
                .locationCode("MAIN")
                .initialQuantity(10L)
                .reason("Tồn kho kiểm thử idempotency MySQL")
                .referenceId("MYSQL-IDEMPOTENCY")
                .build());
        String owner = "mysql-idempotency-owner";
        CartResponse cart = cartService.addItem(
                owner,
                AddCartItemRequest.builder()
                        .sku("MYSQL-IDEMPOTENCY-01")
                        .quantity(2)
                        .build());
        CheckoutOrderRequest request = CheckoutOrderRequest.builder()
                .expectedCartVersion(cart.getVersion())
                .build();
        String key = "mysql-idempotency-key-001";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<CheckoutAttempt> attempts;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<CheckoutAttempt>> futures = IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        return attemptCheckout(owner, key, request);
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = futures.stream().map(this::getCheckoutAttempt).toList();
        }

        List<UUID> successfulOrderIds = attempts.stream()
                .map(CheckoutAttempt::response)
                .filter(Objects::nonNull)
                .map(OrderSnapshotResponse::getId)
                .distinct()
                .toList();
        List<ErrorCode> errorCodes = attempts.stream()
                .map(CheckoutAttempt::errorCode)
                .filter(Objects::nonNull)
                .toList();
        assertThat(successfulOrderIds).hasSize(1);
        assertThat(errorCodes)
                .hasSizeLessThanOrEqualTo(1)
                .allMatch(errorCode -> errorCode == ErrorCode.CHECKOUT_ALREADY_PROCESSING);

        UUID replayedOrderId =
                checkoutIdempotencyService.checkout(owner, key, request).getId();
        assertThat(replayedOrderId).isEqualTo(successfulOrderIds.getFirst());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_don_dat_hang WHERE owner_subject = ?", Integer.class, owner))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_yeu_cau_luy_dang WHERE owner_subject = ?", Integer.class, owner))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id = UNHEX(REPLACE(?, '-', '')) "
                                + "AND movement_type = 'RESERVATION'",
                        Integer.class,
                        stock.getId().toString()))
                .isEqualTo(1);
    }

    @Test
    void executesCatalogAggregateAndStablePaginationOnMySql() {
        CategoryResponse category = catalogCategoryService.create(CreateCategoryRequest.builder()
                .code("MYSQL_CATALOG")
                .name("MySQL Catalog")
                .slug("mysql-catalog")
                .build());
        ProductResponse beta = catalogProductService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Beta")
                .slug("mysql-beta")
                .build());
        catalogProductService.addVariant(
                beta.getId(),
                CreateProductVariantRequest.builder()
                        .sku("MYSQL-BETA-01")
                        .name("Default")
                        .price(new java.math.BigDecimal("19.90"))
                        .currency("USD")
                        .productVersion(beta.getVersion())
                        .build());
        catalogProductService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("MySQL Alpha")
                .slug("mysql-alpha")
                .build());

        ProductPageResponse page = catalogProductService.search(ProductSearchRequest.builder()
                .categoryId(category.getId())
                .page(0)
                .size(1)
                .sortBy(ProductSortField.NAME)
                .direction(CatalogSortDirection.ASC)
                .build());
        List<ProductImageResponse> images = productImageService.upload(
                beta.getId(), uploadRequest(new MockMultipartFile("files", "mysql.png", "image/png", validPngBytes())));

        assertThat(page.getContent()).extracting(ProductResponse::getName).containsExactly("MySQL Alpha");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isFalse();
        assertThat(images).singleElement().satisfies(image -> {
            assertThat(image.isPrimary()).isTrue();
            assertThat(image.getObjectKey()).startsWith("catalog/products/" + beta.getId() + "/");
        });
        Map<String, Object> storedImage = jdbcTemplate.queryForMap(
                "SELECT content_type, size_bytes, CAST(primary_image AS UNSIGNED) AS primary_image, display_order "
                        + "FROM san_pham_hinh_anh WHERE HEX(product_id) = REPLACE(UPPER(?), '-', '')",
                beta.getId().toString());
        assertThat(storedImage)
                .containsEntry("content_type", "image/png")
                .containsEntry("size_bytes", (long) validPngBytes().length)
                .containsEntry("display_order", 0);
        assertThat(((Number) storedImage.get("primary_image")).intValue()).isEqualTo(1);
    }

    @Test
    void keepsCaptchaStateConsistentUnderConcurrentMySqlWrites() throws Exception {
        String username = "mysql-captcha-concurrency";
        int concurrentFailures = 8;
        CountDownLatch ready = new CountDownLatch(concurrentFailures);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrentFailures)) {
            List<? extends Future<?>> futures = IntStream.range(0, concurrentFailures)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        adaptiveCaptchaService.recordFailure(username);
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        String principalHash = hash(username);
        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT failure_count FROM xac_thuc_dang_nhap_that_bai WHERE principal_hash = ?",
                Integer.class,
                principalHash);
        assertThat(failureCount).isEqualTo(concurrentFailures);

        adaptiveCaptchaService.issueChallenge(username);
        adaptiveCaptchaService.issueChallenge(username);
        Integer activeChallenges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM xac_thuc_thu_thach_captcha WHERE principal_hash = ?",
                Integer.class,
                principalHash);
        assertThat(activeChallenges).isEqualTo(1);
    }

    @Test
    void updatesProfilesAndRevokesSessionsOnMySql() {
        String username = "mysql-profile-user";
        String currentPassword = "Str0ngPassword!";
        String newPassword = "An0therStrongPassword!";
        registrationService.register(RegisterUserRequest.builder()
                .username(username)
                .email("mysql-profile-user@example.com")
                .password(currentPassword)
                .build());
        AuthenticationResponse session = authenticationService.authenticate(AuthenticationRequest.builder()
                .username(username)
                .password(currentPassword)
                .build());

        UserResponse updatedProfile = userProfileService.updateProfile(
                username,
                UpdateProfileRequest.builder()
                        .email("updated-mysql-profile@example.com")
                        .firstName("MySQL")
                        .build());
        userProfileService.changePassword(
                username,
                ChangePasswordRequest.builder()
                        .currentPassword(currentPassword)
                        .newPassword(newPassword)
                        .build());

        assertThat(updatedProfile.getEmail()).isEqualTo("updated-mysql-profile@example.com");
        Integer activeSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM xac_thuc_phien_lam_moi refresh_token
                  JOIN xac_thuc_nguoi_dung shop_user ON shop_user.id = refresh_token.user_id
                 WHERE shop_user.username = ?
                   AND refresh_token.revoked_at IS NULL
                """, Integer.class, username);
        assertThat(activeSessions).isZero();
        assertThat(session.getRefreshToken()).isNotBlank();
        assertThat(authenticationService
                        .authenticate(AuthenticationRequest.builder()
                                .username(username)
                                .password(newPassword)
                                .build())
                        .isAuthenticated())
                .isTrue();
    }

    @Test
    void serializesConcurrentRefreshesAndRevokesTheReplayedTokenFamily() throws Exception {
        String username = "mysql-refresh-concurrency";
        registrationService.register(RegisterUserRequest.builder()
                .username(username)
                .email("mysql-refresh-concurrency@example.com")
                .password("Str0ngPassword!")
                .build());
        AuthenticationResponse session = authenticationService.authenticate(AuthenticationRequest.builder()
                .username(username)
                .password("Str0ngPassword!")
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<RefreshAttempt> attempts;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<RefreshAttempt>> futures = IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        await(start);
                        return attemptRefresh(session.getRefreshToken());
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            attempts = futures.stream().map(this::getAttempt).toList();
        }

        assertThat(attempts).filteredOn(attempt -> attempt.response() != null).hasSize(1);
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() == ErrorCode.REFRESH_TOKEN_REUSED)
                .hasSize(1);
        AuthenticationResponse rotatedSession = attempts.stream()
                .map(RefreshAttempt::response)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        IntrospectResponse introspection = authenticationService.introspect(IntrospectRequest.builder()
                .token(rotatedSession.getAccessToken())
                .build());
        assertThat(introspection.isValid()).isFalse();

        Integer activeSessions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM xac_thuc_phien_lam_moi refresh_token
                  JOIN xac_thuc_nguoi_dung shop_user ON shop_user.id = refresh_token.user_id
                 WHERE shop_user.username = ?
                   AND refresh_token.revoked_at IS NULL
                """, Integer.class, username);
        assertThat(activeSessions).isZero();
    }

    private RefreshAttempt attemptRefresh(String refreshToken) {
        try {
            return new RefreshAttempt(
                    authenticationService.refreshToken(
                            RefreshRequest.builder().token(refreshToken).build()),
                    null);
        } catch (AppException exception) {
            return new RefreshAttempt(null, exception.getErrorCode());
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected refresh failure", exception);
        }
    }

    private CheckoutAttempt attemptCheckout(String owner, String key, CheckoutOrderRequest request) {
        try {
            return new CheckoutAttempt(checkoutIdempotencyService.checkout(owner, key, request), null);
        } catch (AppException exception) {
            return new CheckoutAttempt(null, exception.getErrorCode());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unexpected checkout idempotency failure", exception);
        }
    }

    private CheckoutAttempt getCheckoutAttempt(Future<CheckoutAttempt> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent checkout did not complete", exception);
        }
    }

    private RefreshAttempt getAttempt(Future<RefreshAttempt> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent refresh did not complete", exception);
        }
    }

    private String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent MySQL writes", exception);
        }
    }

    private UploadProductImagesRequest uploadRequest(MockMultipartFile file) {
        UploadProductImagesRequest request = new UploadProductImagesRequest();
        request.setFiles(List.of(file));
        return request;
    }

    private byte[] validPngBytes() {
        try {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
            try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                if (!javax.imageio.ImageIO.write(image, "png", output)) {
                    throw new IllegalStateException("PNG writer is unavailable");
                }
                return output.toByteArray();
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not create test PNG", exception);
        }
    }

    private record RefreshAttempt(AuthenticationResponse response, ErrorCode errorCode) {}

    private record CheckoutAttempt(OrderSnapshotResponse response, ErrorCode errorCode) {}
}
