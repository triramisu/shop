package com.shop.order;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.request.VersionedCatalogRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.identity.support.IdentityApiTestClient;
import java.math.BigDecimal;
import java.util.UUID;
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
class CartIntegrationTests {

    private static final String CART_BASE = "/api/cart";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    private IdentityApiTestClient identityClient;

    @BeforeEach
    void setUpIdentityClient() {
        identityClient = new IdentityApiTestClient(mockMvc);
    }

    @AfterEach
    void cleanCommittedCarts() {
        jdbcTemplate.update("DELETE FROM don_hang_muc_gio_hang");
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang");
    }

    @Test
    void requiresAuthenticationAndMergesUpdatesThenRemovesAnOwnedItem() throws Exception {
        String sku = createPublishedSku("FLOW", "CART-FLOW-001");
        String ownerToken = registerAndAuthenticate("cart-flow-owner");
        String otherToken = registerAndAuthenticate("cart-flow-other");

        mockMvc.perform(get(CART_BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.result").doesNotExist());

        MvcResult added = mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(sku, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.result.distinctItemCount").value(1))
                .andExpect(jsonPath("$.result.totalQuantity").value(2))
                .andExpect(jsonPath("$.result.items[0].sku").value(sku))
                .andExpect(jsonPath("$.result.items[0].quantity").value(2))
                .andExpect(jsonPath("$.result.items[0].price").doesNotExist())
                .andReturn();
        UUID itemId = UUID.fromString(JsonPath.read(added.getResponse().getContentAsString(), "$.result.items[0].id"));

        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(sku.toLowerCase(), 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.result.totalQuantity").value(5));

        mockMvc.perform(put(CART_BASE + "/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quantityJson(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalQuantity").value(4))
                .andExpect(jsonPath("$.result.items[0].quantity").value(4));

        mockMvc.perform(delete(CART_BASE + "/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1301))
                .andExpect(jsonPath("$.result").doesNotExist());

        mockMvc.perform(get(CART_BASE).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalQuantity").value(4));

        mockMvc.perform(delete(CART_BASE + "/items/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.totalQuantity").value(0));
    }

    @Test
    void clearOnlyAffectsTheAuthenticatedOwnersCart() throws Exception {
        String sku = createPublishedSku("CLEAR", "CART-CLEAR-001");
        String firstToken = registerAndAuthenticate("cart-clear-first");
        String secondToken = registerAndAuthenticate("cart-clear-second");
        addItem(firstToken, sku, 2);
        addItem(secondToken, sku, 3);

        mockMvc.perform(delete(CART_BASE + "/items").header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.totalQuantity").value(0));

        mockMvc.perform(get(CART_BASE).header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.totalQuantity").value(3));
    }

    @Test
    void validatesSkuQuantitySellabilityAndMergedQuantityLimit() throws Exception {
        String sku = createPublishedSku("VALIDATE", "CART-VALIDATE-001");
        String draftSku = createCatalogSku("DRAFT", "CART-DRAFT-001", false);
        String token = registerAndAuthenticate("cart-validate-owner");

        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("UNKNOWN-SKU", 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1300))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(draftSku, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1300));
        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("!", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1303));
        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(sku, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1305));
        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"" + sku + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1304));

        addItem(token, sku, 99);
        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(sku, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1306))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    private String registerAndAuthenticate(String username) throws Exception {
        identityClient.register(username);
        return identityClient.authenticate(username).accessToken();
    }

    private void addItem(String token, String sku, int quantity) throws Exception {
        mockMvc.perform(post(CART_BASE + "/items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(sku, quantity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000));
    }

    private String createPublishedSku(String suffix, String sku) {
        return createCatalogSku(suffix, sku, true);
    }

    private String createCatalogSku(String suffix, String sku, boolean publish) {
        String slugSuffix = suffix.toLowerCase();
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("CART_" + suffix)
                .name("Cart " + suffix)
                .slug("cart-" + slugSuffix)
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Cart product " + suffix)
                .slug("cart-product-" + slugSuffix)
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku(sku)
                        .name("Default")
                        .price(new BigDecimal("49.90"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        if (publish) {
            productService.publish(
                    product.getId(),
                    VersionedCatalogRequest.builder()
                            .version(product.getVersion())
                            .build());
        }
        return sku;
    }

    private String itemJson(String sku, int quantity) {
        return """
                {"sku":"%s","quantity":%d}
                """.formatted(sku, quantity);
    }

    private String quantityJson(int quantity) {
        return """
                {"quantity":%d}
                """.formatted(quantity);
    }
}
