package com.shop.order;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.dto.response.ProductVariantResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.identity.support.IdentityApiTestClient;
import com.shop.inventory.internal.dto.request.CreateStockItemRequest;
import com.shop.inventory.internal.dto.response.StockItemResponse;
import com.shop.inventory.internal.service.StockInventoryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutOrderIdempotencyApiTests {

    private static final String CART_ITEMS = "/api/cart/items";
    private static final String CHECKOUT_ORDERS = "/api/checkout/orders";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    @Autowired
    private StockInventoryService stockInventoryService;

    private IdentityApiTestClient identityClient;
    private final List<String> registeredUsernames = new ArrayList<>();

    @BeforeEach
    void setUpIdentityClient() {
        identityClient = new IdentityApiTestClient(mockMvc);
    }

    @AfterEach
    void cleanCommittedTestData() {
        jdbcTemplate.update("DELETE FROM don_hang_yeu_cau_luy_dang WHERE owner_subject LIKE 'idempotency-%'");
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang WHERE owner_subject LIKE 'idempotency-%'");
        jdbcTemplate.update("""
                DELETE FROM don_hang_muc_gio_hang
                 WHERE cart_id IN (
                    SELECT id FROM don_hang_gio_hang WHERE owner_subject LIKE 'idempotency-%'
                 )
                """);
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang WHERE owner_subject LIKE 'idempotency-%'");
        jdbcTemplate.update("""
                DELETE FROM ton_kho_yeu_cau_luy_dang
                 WHERE result_stock_item_id IN (
                    SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'IDEMP-%'
                 )
                """);
        jdbcTemplate.update("""
                DELETE FROM ton_kho_giu_hang
                 WHERE stock_item_id IN (SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'IDEMP-%')
                """);
        jdbcTemplate.update("""
                DELETE FROM ton_kho_bien_dong
                 WHERE stock_item_id IN (SELECT id FROM ton_kho_mat_hang WHERE sku LIKE 'IDEMP-%')
                """);
        jdbcTemplate.update("DELETE FROM ton_kho_mat_hang WHERE sku LIKE 'IDEMP-%'");
        jdbcTemplate.update("DELETE FROM san_pham_bien_the WHERE sku LIKE 'IDEMP-%'");
        jdbcTemplate.update("DELETE FROM san_pham_san_pham WHERE slug LIKE 'idemp-%'");
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code LIKE 'IDEMP_%'");
        for (String username : registeredUsernames) {
            jdbcTemplate.update("""
                    DELETE FROM xac_thuc_phien_lam_moi
                     WHERE user_id = (SELECT id FROM xac_thuc_nguoi_dung WHERE username = ?)
                    """, username);
            jdbcTemplate.update("""
                    DELETE FROM xac_thuc_nguoi_dung_vai_tro
                     WHERE user_id = (SELECT id FROM xac_thuc_nguoi_dung WHERE username = ?)
                    """, username);
            jdbcTemplate.update("DELETE FROM xac_thuc_nguoi_dung WHERE username = ?", username);
        }
        registeredUsernames.clear();
    }

    @Test
    void requiresAuthenticationAValidKeyAndAValidCartVersion() throws Exception {
        mockMvc.perform(post(CHECKOUT_ORDERS)
                        .header(IDEMPOTENCY_HEADER, "idempotency-unauthenticated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        String token = registerAndAuthenticate("idempotency-validation");
        mockMvc.perform(post(CHECKOUT_ORDERS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1320));
        mockMvc.perform(post(CHECKOUT_ORDERS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(IDEMPOTENCY_HEADER, "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1321));
        mockMvc.perform(post(CHECKOUT_ORDERS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(IDEMPOTENCY_HEADER, "idempotency-missing-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1318));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(CHECKOUT_ORDERS)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .header(IDEMPOTENCY_HEADER, "idempotency-empty-cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderJson(0)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(1314));
        }
        assertThat(idempotencyCount("idempotency-validation")).isEqualTo(1);
    }

    @Test
    void replaysTheSameOrderWithoutCreatingAnotherReservation() throws Exception {
        String owner = "idempotency-replay";
        String token = registerAndAuthenticate(owner);
        CatalogItem item = createPublishedItem();
        StockItemResponse stock = createStock(item.sku(), 10);
        long cartVersion = addCartItem(token, item.sku(), 2);
        String key = "idempotency-replay-order-001";

        UUID firstOrderId = createOrder(token, key, cartVersion);
        UUID replayedOrderId = createOrder(token, key, cartVersion);

        assertThat(replayedOrderId).isEqualTo(firstOrderId);
        assertThat(orderCount(owner)).isEqualTo(1);
        assertThat(idempotencyCount(owner)).isEqualTo(1);
        assertThat(reservationMovementCount(stock.getId())).isEqualTo(1);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT idempotency_key_hash FROM don_hang_yeu_cau_luy_dang WHERE owner_subject = ?",
                String.class,
                owner);
        assertThat(storedHash).hasSize(64).isNotEqualTo(key);
    }

    @Test
    void rejectsDifferentPayloadAndScopesTheSameKeyByOwner() throws Exception {
        CatalogItem item = createPublishedItem();
        createStock(item.sku(), 10);
        String sharedKey = "idempotency-shared-scope-001";

        String firstOwner = "idempotency-scope-first";
        String firstToken = registerAndAuthenticate(firstOwner);
        long firstVersion = addCartItem(firstToken, item.sku(), 1);
        UUID firstOrder = createOrder(firstToken, sharedKey, firstVersion);
        mockMvc.perform(post(CHECKOUT_ORDERS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                        .header(IDEMPOTENCY_HEADER, sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(firstVersion + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1322));

        String secondOwner = "idempotency-scope-second";
        String secondToken = registerAndAuthenticate(secondOwner);
        long secondVersion = addCartItem(secondToken, item.sku(), 1);
        UUID secondOrder = createOrder(secondToken, sharedKey, secondVersion);

        assertThat(secondOrder).isNotEqualTo(firstOrder);
        assertThat(orderCount(firstOwner)).isEqualTo(1);
        assertThat(orderCount(secondOwner)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM don_hang_yeu_cau_luy_dang WHERE idempotency_key_hash = "
                                + "(SELECT idempotency_key_hash FROM don_hang_yeu_cau_luy_dang "
                                + "WHERE owner_subject = ?)",
                        Integer.class,
                        firstOwner))
                .isEqualTo(2);
    }

    private String registerAndAuthenticate(String username) throws Exception {
        identityClient.register(username);
        registeredUsernames.add(username);
        return identityClient.authenticate(username).accessToken();
    }

    private long addCartItem(String token, String sku, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post(CART_ITEMS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","quantity":%d}
                                """.formatted(sku, quantity)))
                .andExpect(status().isCreated())
                .andReturn();
        Number version = JsonPath.read(result.getResponse().getContentAsString(), "$.result.version");
        return version.longValue();
    }

    private UUID createOrder(String token, String key, long cartVersion) throws Exception {
        MvcResult result = mockMvc.perform(post(CHECKOUT_ORDERS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(IDEMPOTENCY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson(cartVersion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.result.id"));
    }

    private CatalogItem createPublishedItem() {
        int sequence = SEQUENCE.incrementAndGet();
        String suffix = Integer.toString(sequence);
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("IDEMP_" + suffix)
                .name("Idempotency " + suffix)
                .slug("idemp-" + suffix)
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Idempotency product " + suffix)
                .slug("idemp-product-" + suffix)
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku("IDEMP-SKU-" + suffix)
                        .name("Idempotency variant " + suffix)
                        .price(new BigDecimal("18.50"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        product = productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        ProductVariantResponse variant = product.getVariants().getFirst();
        return new CatalogItem(variant.getSku());
    }

    private StockItemResponse createStock(String sku, long quantity) {
        return stockInventoryService.create(CreateStockItemRequest.builder()
                .sku(sku)
                .locationCode("MAIN")
                .initialQuantity(quantity)
                .reason("Tồn đầu kỳ cho idempotency checkout")
                .referenceId("IDEMP-SETUP")
                .build());
    }

    private int orderCount(String owner) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM don_hang_don_dat_hang WHERE owner_subject = ?", Integer.class, owner);
    }

    private int idempotencyCount(String owner) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM don_hang_yeu_cau_luy_dang WHERE owner_subject = ?", Integer.class, owner);
    }

    private int reservationMovementCount(UUID stockItemId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ton_kho_bien_dong WHERE stock_item_id = ? AND movement_type = 'RESERVATION'",
                Integer.class,
                stockItemId);
    }

    private String orderJson(long expectedCartVersion) {
        return """
                {"expectedCartVersion":%d}
                """.formatted(expectedCartVersion);
    }

    private record CatalogItem(String sku) {}
}
