package com.shop.order.purchase;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.identity.support.IdentityApiTestClient;
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.service.StockInventoryService;
import com.shop.payment.event.PaymentStatusChangedEvent;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
        properties = {
            "app.payment.provider.type=vnpay",
            "app.payment.vnpay.tmn-code=TEST1234",
            "app.payment.vnpay.hash-secret=purchase-test-only-signature-material-0123456789",
            "app.payment.vnpay.public-base-url=https://api.example.com",
            "app.payment.vnpay.allowed-hosts=api.example.com",
            "CHECKOUT_INVENTORY_LOCATION_CODE=DEMO-LAB",
            "app.order.payment.auto-initiation-enabled=true",
            "app.order.payment.event-consumption-enabled=true",
            "app.order.payment.recovery.enabled=false",
            "app.payment.vnpay.query.enabled=false",
            "app.payment.reconciliation.enabled=false"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VnpayPurchaseFlowMySqlTests {
    private static final String SECRET = "purchase-test-only-signature-material-0123456789";
    private static final String IPN = "/api/payments/webhooks/vnpay";
    private static final String BROWSER_RETURN = "/api/payments/checkout/vnpay/return";

    // Dedicated disposable database; no environment-provided RDS URL or merchant credentials.
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("vnpay_purchase_test")
            .withUsername("purchase_test")
            .withPassword("purchase-local-test-only-password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/mysql");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CatalogCategoryService categories;

    @Autowired
    CatalogProductService products;

    @Autowired
    StockInventoryService stock;

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void successfulPurchaseSurvivesReloadAndLogoutWithoutDoubleCaptureOrStockMovement() throws Exception {
        Flow flow = checkout();
        var payload = signed(callback(flow));
        notify(payload, "00");
        notify(payload, "02");

        assertOutcome(flow, "SUCCEEDED", "PAID", 8, 0, 1, 0);
        assertThat(webhookCount(flow)).isEqualTo(1);
        assertThat(terminalOrderEvents(flow, "SUCCEEDED", "COMPLETED")).isEqualTo(1);
        payment(flow).andExpect(jsonPath("result.actionUrl").isEmpty());
        mvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(flow.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.totalElements").value(1))
                .andExpect(jsonPath("result.content[0].id").value(flow.orderId()));

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IdentityApiTestClient.tokenJson(flow.tokens().refreshToken())))
                .andExpect(status().isOk());
        mvc.perform(get(orderPath(flow))
                        .header(HttpHeaders.AUTHORIZATION, bearer(flow.tokens().accessToken())))
                .andExpect(status().isUnauthorized());
        var newTokens = new IdentityApiTestClient(mvc).authenticate(flow.owner());
        mvc.perform(get(orderPath(flow)).header(HttpHeaders.AUTHORIZATION, bearer(newTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.status").value("PAID"));
        assertThat(movements(flow, "CONFIRMATION")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"09", "24", "51"})
    void definitiveFailureReleasesStockOnceAndCannotBeOverwrittenByLateSuccess(String code) throws Exception {
        Flow flow = checkout();
        var fields = callback(flow);
        fields.put("vnp_ResponseCode", code);
        fields.put("vnp_TransactionStatus", "02");
        notify(signed(fields), "00");
        notify(signed(fields), "02");
        notify(signed(callback(flow)), "02");

        assertOutcome(flow, "FAILED", "CANCELLED", 10, 0, 0, 1);
        assertThat(webhookCount(flow)).isEqualTo(1);
        assertThat(terminalOrderEvents(flow, "FAILED", "COMPLETED")).isEqualTo(1);
    }

    @Test
    void browserSuccessEvenWithValidSignatureNeverMarksOrderPaidOrConfirmsStock() throws Exception {
        Flow flow = checkout();
        query(BROWSER_RETURN, signed(callback(flow)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.signatureVerified").value(true))
                .andExpect(jsonPath("result.paymentConfirmedByBrowser").value(false));
        assertPending(flow);
        assertThat(webhookCount(flow)).isZero();
        notify(signed(callback(flow)), "00");
        assertOutcome(flow, "SUCCEEDED", "PAID", 8, 0, 1, 0);
    }

    @Test
    void checkoutReplayKeepsOneOrderAttemptAndReservationAndRejectsChangedPayload() throws Exception {
        Flow flow = checkout();
        var replay = placeOrder(flow.tokens(), flow.key(), flow.cartVersion())
                .andExpect(status().isCreated())
                .andReturn();
        assertThat((String) JsonPath.read(replay.getResponse().getContentAsString(), "result.id"))
                .isEqualTo(flow.orderId());
        placeOrder(flow.tokens(), flow.key(), flow.cartVersion() + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value(1322));
        assertThat(count("SELECT COUNT(*) FROM don_hang_don_dat_hang WHERE owner_subject=?", flow.owner()))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT COUNT(*) FROM thanh_toan_lan_thu WHERE order_id=UNHEX(REPLACE(?,'-',''))",
                        flow.orderId()))
                .isEqualTo(1);
        assertThat(movements(flow, "RESERVATION")).isEqualTo(1);
        assertPending(flow);
    }

    @Test
    void invalidSignatureAndSignedWrongAmountDoNotPoisonTheValidRetry() throws Exception {
        Flow flow = checkout();
        var tampered = signed(callback(flow));
        tampered.put("vnp_Amount", "100");
        notify(tampered, "97");
        var wrongAmount = callback(flow);
        wrongAmount.put("vnp_Amount", "100");
        notify(signed(wrongAmount), "04");
        assertPending(flow);
        assertThat(webhookCount(flow)).isZero();
        notify(signed(callback(flow)), "00");
        assertOutcome(flow, "SUCCEEDED", "PAID", 8, 0, 1, 0);
    }

    @Test
    void signedMismatchedOrderAndUnknownReferenceCannotSettleEitherOrder() throws Exception {
        Flow first = checkout();
        Flow other = checkout();
        var fields = callback(first);
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + other.orderId());
        notify(signed(fields), "01");
        fields = callback(first);
        fields.put("vnp_TxnRef", UUID.randomUUID().toString().replace("-", ""));
        notify(signed(fields), "01");
        assertPending(first);
        assertPending(other);
        assertThat(webhookCount(first)).isZero();
        assertThat(webhookCount(other)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "07", "99"})
    void uncertainProviderStatusKeepsOrderPendingAndInventoryReserved(String code) throws Exception {
        Flow flow = checkout();
        var fields = callback(flow);
        fields.put("vnp_ResponseCode", code);
        fields.put("vnp_TransactionStatus", "01");
        notify(signed(fields), "99");
        assertPending(flow);
        assertThat(webhookCount(flow)).isZero();
    }

    @Test
    void concurrentIpnsCommitOnePaymentOneOrderEventAndOneConfirmation() throws Exception {
        Flow flow = checkout();
        var payload = signed(callback(flow));
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("IPN start timeout");
                    var result = query(IPN, payload).andExpect(status().isOk()).andReturn();
                    return JsonPath.read(result.getResponse().getContentAsString(), "RspCode");
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<String> receipts = new ArrayList<>();
            for (var future : futures) receipts.add(future.get(30, TimeUnit.SECONDS));
            assertThat(receipts).containsOnly("00", "02");
            assertThat(receipts.stream().filter("00"::equals).count()).isEqualTo(1);
        }
        assertOutcome(flow, "SUCCEEDED", "PAID", 8, 0, 1, 0);
        assertThat(webhookCount(flow)).isEqualTo(1);
        assertThat(terminalOrderEvents(flow, "SUCCEEDED", "COMPLETED")).isEqualTo(1);
    }

    @Test
    void successAfterReservationExpiryRecordsFundsButDoesNotPretendFulfillablePaidOrder() throws Exception {
        Flow flow = checkout();
        assertThat(jdbc.update("""
                UPDATE ton_kho_giu_hang SET expires_at=TIMESTAMPADD(MICROSECOND,1,created_at)
                WHERE stock_item_id=UNHEX(REPLACE(?,'-',''))
                """, flow.stockId())).isEqualTo(1);
        notify(signed(callback(flow)), "00");
        assertOutcome(flow, "SUCCEEDED", "PENDING", 10, 0, 0, 0);
        assertThat(movements(flow, "EXPIRATION")).isEqualTo(1);
        assertThat(terminalOrderEvents(flow, "SUCCEEDED", "MANUAL_ACTION_REQUIRED"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM don_hang_dieu_phoi_ton_kho WHERE order_id=UNHEX(REPLACE(?,'-',''))
                """, String.class, flow.orderId())).isEqualTo("PAYMENT_RECOVERY_REQUIRED");
        notify(signed(callback(flow)), "02");
        assertThat(movements(flow, "CONFIRMATION")).isZero();
        assertThat(movements(flow, "EXPIRATION")).isEqualTo(1);
    }

    @Test
    void paymentLinkRequiresOwnerAndCannotBeUsedToReadAnotherCustomersOrder() throws Exception {
        Flow flow = checkout();
        Flow other = checkout();
        String path = orderPath(flow) + "/payment";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(1310));
        mvc.perform(get(orderPath(flow))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.tokens().accessToken())))
                .andExpect(status().isNotFound());
        payment(flow).andExpect(header().string("Cache-Control", "private, no-store"));
        assertPending(flow);
        assertPending(other);
    }

    @Test
    void lateFailureAndChangedReplayCannotUndoAnAlreadyPaidOrder() throws Exception {
        Flow flow = checkout();
        notify(signed(callback(flow)), "00");
        var changed = callback(flow);
        changed.put("vnp_BankCode", "OTHER");
        notify(signed(changed), "99");
        var failed = callback(flow);
        failed.put("vnp_ResponseCode", "24");
        failed.put("vnp_TransactionStatus", "02");
        notify(signed(failed), "02");
        assertOutcome(flow, "SUCCEEDED", "PAID", 8, 0, 1, 0);
        assertThat(webhookCount(flow)).isEqualTo(1);
    }

    @Test
    void eventPublicationFailureRollsBackPaymentAndInboxBeforeValidRetryPaysOrder() throws Exception {
        Flow flow = checkout();
        ApplicationListener<ApplicationEvent> fault = event -> {
            if (event instanceof PayloadApplicationEvent<?> payload
                    && payload.getPayload() instanceof PaymentStatusChangedEvent changed
                    && changed.orderId().toString().equals(flow.orderId())) {
                throw new IllegalStateException("Local purchase test publication fault");
            }
        };
        context.addApplicationListener(fault);
        try {
            notify(signed(callback(flow)), "99");
        } finally {
            context.removeApplicationListener(fault);
        }
        assertPending(flow);
        assertThat(webhookCount(flow)).isZero();
        assertThat(terminalOrderEvents(flow, "SUCCEEDED", "COMPLETED")).isZero();
        notify(signed(callback(flow)), "00");
        assertOutcome(flow, "SUCCEEDED", "PAID", 8, 0, 1, 0);
    }

    @Test
    void stockAtAnotherLocationCancelsBeforePaymentWithoutReservationOrStockMutation() throws Exception {
        Flow flow = checkout("MAIN", false);
        mvc.perform(get(orderPath(flow))
                        .header(HttpHeaders.AUTHORIZATION, bearer(flow.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject(
                        "SELECT failure_code FROM don_hang_dieu_phoi_ton_kho WHERE order_id=UNHEX(REPLACE(?,'-',''))",
                        String.class,
                        flow.orderId()))
                .isEqualTo("STOCK_ITEM_NOT_FOUND");
        assertThat(count(
                        "SELECT COUNT(*) FROM thanh_toan_lan_thu WHERE order_id=UNHEX(REPLACE(?,'-',''))",
                        flow.orderId()))
                .isZero();
        var inventory = stock.getById(UUID.fromString(flow.stockId()));
        assertThat(inventory.getOnHand()).isEqualTo(10);
        assertThat(inventory.getReserved()).isZero();
        assertThat(movements(flow, "RESERVATION")).isZero();
        assertThat(movements(flow, "CONFIRMATION")).isZero();
        assertThat(movements(flow, "RELEASE")).isZero();
    }

    private Flow checkout() throws Exception {
        return checkout("DEMO-LAB", true);
    }

    private Flow checkout(String stockLocation, boolean handoffExpected) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String owner = "purchase-" + suffix;
        var client = new IdentityApiTestClient(mvc);
        client.register(owner);
        var tokens = client.authenticate(owner);
        var category = categories.create(CreateCategoryRequest.builder()
                .code("PURCHASE_" + suffix)
                .name("Local purchase fixture")
                .slug("purchase-" + suffix)
                .build());
        var product = products.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Local VNPay product")
                .slug("purchase-product-" + suffix)
                .build());
        String sku = "PURCHASE-" + suffix;
        product = products.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku(sku)
                        .name("Fixture variant")
                        .price(new BigDecimal("199000.00"))
                        .currency("VND")
                        .productVersion(product.getVersion())
                        .build());
        products.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        String stockId = stock.create(CreateStockItemRequest.builder()
                        .sku(sku)
                        .locationCode(stockLocation)
                        .initialQuantity(10L)
                        .reason("Local-only purchase fixture")
                        .referenceId("PURCHASE-" + suffix)
                        .build())
                .getId()
                .toString();
        mvc.perform(get("/api/catalog/products")
                        .param("keyword", "Local VNPay")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.totalElements").value(1))
                .andExpect(
                        jsonPath("result.content[0].id").value(product.getId().toString()));
        var cart = mvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andReturn();
        long version = ((Number) JsonPath.read(cart.getResponse().getContentAsString(), "result.version")).longValue();
        String key = "purchase-checkout-" + suffix;
        var order =
                placeOrder(tokens, key, version).andExpect(status().isCreated()).andReturn();
        String orderJson = order.getResponse().getContentAsString();
        String orderId = JsonPath.read(orderJson, "result.id");
        BigDecimal total =
                new BigDecimal(JsonPath.read(orderJson, "result.grandTotal").toString());
        assertThat(total).isEqualByComparingTo("398000.00");
        assertThat((String) JsonPath.read(orderJson, "result.currency")).isEqualTo("VND");
        if (!handoffExpected) {
            return new Flow(owner, tokens, orderId, null, stockId, total, key, version);
        }
        var result = mvc.perform(get("/api/orders/" + orderId + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.providerCode").value("VNPAY"))
                .andExpect(jsonPath("result.status").value("REQUIRES_ACTION"))
                .andReturn();
        String paymentJson = result.getResponse().getContentAsString();
        String attemptId = JsonPath.read(paymentJson, "result.paymentAttemptId");
        URI action = URI.create(JsonPath.read(paymentJson, "result.actionUrl"));
        assertThat(action.getHost()).isEqualTo("api.example.com");
        // Dispatch only to the local controller, never follow the external provider redirect.
        var handoff = mvc.perform(get(action.getPath() + "?" + action.getRawQuery()))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        assertThat(handoff.getResponse().getHeader("Location"))
                .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")
                .doesNotContain(SECRET);
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.11");
        return new Flow(owner, tokens, orderId, attemptId, stockId, total, key, version);
    }

    private ResultActions placeOrder(IdentityApiTestClient.TokenPair tokens, String key, long version)
            throws Exception {
        return mvc.perform(post("/api/checkout/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedCartVersion\":" + version + "}"));
    }

    private Map<String, String> callback(Flow flow) {
        var fields = new TreeMap<String, String>();
        fields.put("vnp_TmnCode", "TEST1234");
        fields.put("vnp_TxnRef", flow.attemptId().replace("-", ""));
        fields.put("vnp_TransactionNo", "123456789");
        fields.put(
                "vnp_Amount", flow.total().movePointRight(2).toBigIntegerExact().toString());
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + flow.orderId());
        fields.put("vnp_BankCode", "NCB");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        return fields;
    }

    // Independent fixture codec; deliberately does not call the production VNPay signer.
    private Map<String, String> signed(Map<String, String> fields) throws Exception {
        var result = new TreeMap<>(fields);
        String canonical = result.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        var mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        result.put("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));
        return result;
    }

    private ResultActions query(String path, Map<String, String> fields) throws Exception {
        var request = get(path);
        fields.forEach(request::queryParam);
        return mvc.perform(request);
    }

    private void notify(Map<String, String> payload, String code) throws Exception {
        query(IPN, payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("RspCode").value(code));
    }

    private ResultActions payment(Flow flow) throws Exception {
        return mvc.perform(get(orderPath(flow) + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(flow.tokens().accessToken())))
                .andExpect(status().isOk());
    }

    private String orderPath(Flow flow) {
        return "/api/orders/" + flow.orderId();
    }

    private void assertPending(Flow flow) throws Exception {
        assertOutcome(flow, "REQUIRES_ACTION", "PENDING", 10, 2, 0, 0);
    }

    private void assertOutcome(
            Flow flow,
            String paymentStatus,
            String orderStatus,
            long onHand,
            long reserved,
            int confirmations,
            int releases)
            throws Exception {
        payment(flow).andExpect(jsonPath("result.status").value(paymentStatus));
        mvc.perform(get(orderPath(flow))
                        .header(HttpHeaders.AUTHORIZATION, bearer(flow.tokens().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.status").value(orderStatus))
                .andExpect(jsonPath("result.grandTotal").value(398000.0));
        var inventory = stock.getById(UUID.fromString(flow.stockId()));
        assertThat(inventory.getOnHand()).isEqualTo(onHand);
        assertThat(inventory.getReserved()).isEqualTo(reserved);
        assertThat(movements(flow, "RESERVATION")).isEqualTo(1);
        assertThat(movements(flow, "CONFIRMATION")).isEqualTo(confirmations);
        assertThat(movements(flow, "RELEASE")).isEqualTo(releases);
    }

    private int movements(Flow flow, String type) {
        return count(
                "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id=UNHEX(REPLACE(?,'-','')) AND movement_type=?",
                flow.stockId(),
                type);
    }

    private int webhookCount(Flow flow) {
        return count(
                "SELECT COUNT(*) FROM thanh_toan_su_kien_webhook WHERE payment_attempt_id=UNHEX(REPLACE(?,'-',''))",
                flow.attemptId());
    }

    private int terminalOrderEvents(Flow flow, String state, String outcome) {
        return count(
                "SELECT COUNT(*) FROM don_hang_su_kien_thanh_toan WHERE order_id=UNHEX(REPLACE(?,'-','')) AND payment_status=? AND outcome=?",
                flow.orderId(),
                state,
                outcome);
    }

    private int count(String query, Object... args) {
        return jdbc.queryForObject(query, Integer.class, args);
    }

    private record Flow(
            String owner,
            IdentityApiTestClient.TokenPair tokens,
            String orderId,
            String attemptId,
            String stockId,
            BigDecimal total,
            String key,
            long cartVersion) {}
}
