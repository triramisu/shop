package com.shop.order;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.UpdateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.dto.response.ProductVariantResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.identity.support.IdentityApiTestClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest(
        properties = {"app.order.checkout.pricing.discount-rate=0.10", "app.order.checkout.pricing.tax-rate=0.08"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutIntegrationTests {

    private static final String CART_ITEMS = "/api/cart/items";
    private static final String CHECKOUT_QUOTE = "/api/checkout/quote";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    private IdentityApiTestClient identityClient;
    private final List<String> registeredUsernames = new ArrayList<>();

    @BeforeEach
    void setUpIdentityClient() {
        identityClient = new IdentityApiTestClient(mockMvc);
    }

    @AfterEach
    void cleanCommittedTestData() {
        jdbcTemplate.update("DELETE FROM don_hang_muc_gio_hang");
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang");
        jdbcTemplate.update("DELETE FROM san_pham_bien_the WHERE sku LIKE 'CHECKOUT-%'");
        jdbcTemplate.update("DELETE FROM san_pham_san_pham WHERE slug LIKE 'checkout-%'");
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code LIKE 'CHECKOUT_%'");
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
    void requiresAuthenticationValidatesCartVersionAndKeepsOwnerIsolation() throws Exception {
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        String ownerToken = registerAndAuthenticate("checkout-owner");
        String otherToken = registerAndAuthenticate("checkout-other");
        CatalogItem item = createPublishedItem("10.00", "USD");

        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1314));

        long cartVersion = addCartItem(ownerToken, item.sku(), 1);
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(cartVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1314));

        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1318));
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(-1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1319));
    }

    @Test
    void usesTheCurrentCatalogPriceAndIgnoresClientSuppliedAmounts() throws Exception {
        String token = registerAndAuthenticate("checkout-authoritative-price");
        CatalogItem item = createPublishedItem("10.00", "USD");
        long cartVersion = addCartItem(token, item.sku(), 2);

        productService.updateVariant(
                item.productId(),
                item.variantId(),
                UpdateProductVariantRequest.builder()
                        .name("Giá hiện hành")
                        .price(new BigDecimal("12.34"))
                        .currency("USD")
                        .version(item.variantVersion())
                        .build());

        String tamperedRequest = """
                {
                  "expectedCartVersion": %d,
                  "unitPrice": 0.01,
                  "subtotal": 0.01,
                  "discount": 999,
                  "tax": 0,
                  "grandTotal": 0.01,
                  "items": []
                }
                """.formatted(cartVersion);
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.currency").value("USD"))
                .andExpect(jsonPath("$.result.discountRate").value(0.10))
                .andExpect(jsonPath("$.result.taxRate").value(0.08))
                .andExpect(jsonPath("$.result.lines", hasSize(1)))
                .andExpect(jsonPath("$.result.lines[0].sku").value(item.sku()))
                .andExpect(jsonPath("$.result.lines[0].name").value("Giá hiện hành"))
                .andExpect(jsonPath("$.result.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.result.lines[0].unitPrice").value(12.34))
                .andExpect(jsonPath("$.result.lines[0].subtotal").value(24.68))
                .andExpect(jsonPath("$.result.lines[0].discount").value(2.47))
                .andExpect(jsonPath("$.result.lines[0].tax").value(1.78))
                .andExpect(jsonPath("$.result.lines[0].total").value(23.99))
                .andExpect(jsonPath("$.result.subtotal").value(24.68))
                .andExpect(jsonPath("$.result.discount").value(2.47))
                .andExpect(jsonPath("$.result.tax").value(1.78))
                .andExpect(jsonPath("$.result.grandTotal").value(23.99));
    }

    @Test
    void rejectsAStaleCartAnUnavailableItemAndMixedCurrencies() throws Exception {
        String staleToken = registerAndAuthenticate("checkout-stale");
        CatalogItem staleItem = createPublishedItem("5.00", "USD");
        long staleVersion = addCartItem(staleToken, staleItem.sku(), 1);
        addCartItem(staleToken, staleItem.sku(), 1);
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(staleToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(staleVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1315));

        String unavailableToken = registerAndAuthenticate("checkout-unavailable");
        CatalogItem unavailableItem = createPublishedItem("7.00", "USD");
        long unavailableCartVersion = addCartItem(unavailableToken, unavailableItem.sku(), 1);
        productService.hide(
                unavailableItem.productId(),
                VersionedCatalogRequest.builder()
                        .version(unavailableItem.productVersion())
                        .build());
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unavailableToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(unavailableCartVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1316));

        String mixedToken = registerAndAuthenticate("checkout-mixed-currency");
        CatalogItem usdItem = createPublishedItem("3.00", "USD");
        CatalogItem eurItem = createPublishedItem("4.00", "EUR");
        addCartItem(mixedToken, usdItem.sku(), 1);
        long mixedCartVersion = addCartItem(mixedToken, eurItem.sku(), 1);
        mockMvc.perform(post(CHECKOUT_QUOTE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mixedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(mixedCartVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1317));
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

    private CatalogItem createPublishedItem(String price, String currency) {
        int sequence = SEQUENCE.incrementAndGet();
        String suffix = Integer.toString(sequence);
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("CHECKOUT_" + suffix)
                .name("Checkout " + suffix)
                .slug("checkout-" + suffix)
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Checkout product " + suffix)
                .slug("checkout-product-" + suffix)
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku("CHECKOUT-SKU-" + suffix)
                        .name("Checkout variant " + suffix)
                        .price(new BigDecimal(price))
                        .currency(currency)
                        .productVersion(product.getVersion())
                        .build());
        product = productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        ProductVariantResponse variant = product.getVariants().getFirst();
        return new CatalogItem(
                product.getId(), product.getVersion(), variant.getId(), variant.getVersion(), variant.getSku());
    }

    private String quoteJson(long expectedCartVersion) {
        return """
                {"expectedCartVersion":%d}
                """.formatted(expectedCartVersion);
    }

    private record CatalogItem(
            java.util.UUID productId, long productVersion, java.util.UUID variantId, long variantVersion, String sku) {}
}
