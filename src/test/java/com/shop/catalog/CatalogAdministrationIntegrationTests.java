package com.shop.catalog;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.support.IdentityApiTestClient;
import jakarta.persistence.EntityManager;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogAdministrationIntegrationTests {

    private static final String BASE = "/api/catalog";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private IdentityApiTestClient identityClient;

    @BeforeEach
    void setUpIdentityClient() {
        identityClient = new IdentityApiTestClient(mockMvc);
    }

    @Test
    void exposesDtoBasedCatalogCrudOnlyToAdministrators() throws Exception {
        String customerToken = accountWithRole("catalog-customer", RoleCode.USER);
        String adminToken = accountWithRole("catalog-admin", RoleCode.ADMIN);

        mockMvc.perform(post(BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("DENIED", "Denied", "denied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        CategoryResult category = createCategory(adminToken, "COMPUTERS", "Computers", "computers");
        ProductResult product = createProduct(
                adminToken, category.id(), "Developer Laptop", "developer-laptop", "Portable workstation");

        mockMvc.perform(get(BASE + "/products/{productId}", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(product.id().toString()))
                .andExpect(jsonPath("$.result.category.code").value("COMPUTERS"))
                .andExpect(jsonPath("$.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.result.variants.length()").value(0))
                .andExpect(jsonPath("$.result.category.products").doesNotExist());

        mockMvc.perform(get(BASE + "/categories").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(category.id().toString()));
    }

    @Test
    void validatesFieldsPageAndSortBeforeCallingTheService() throws Exception {
        String adminToken = accountWithRole("catalog-validation-admin", RoleCode.ADMIN);
        CategoryResult category = createCategory(adminToken, "VALIDATION", "Validation", "validation");
        ProductResult product = createProduct(adminToken, category.id(), "Validated", "validated", null);

        mockMvc.perform(post(BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("BLANK_NAME", "", "blank-name")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1112))
                .andExpect(jsonPath("$.message").value("Tên là bắt buộc"));

        mockMvc.perform(get(BASE + "/products")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1048));

        mockMvc.perform(get(BASE + "/products")
                        .param("sortBy", "PRICE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1010));

        mockMvc.perform(post(BASE + "/products/{productId}/variants", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variantJson("NEGATIVE-SKU", "Invalid", "-0.01", "VND", product.version())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1122));
    }

    @Test
    void updatesCategoryProductAndVariantThroughVersionedDtos() throws Exception {
        String adminToken = accountWithRole("catalog-update-admin", RoleCode.ADMIN);
        CategoryResult category = createCategory(adminToken, "TABLETS", "Tablets", "tablets");

        MvcResult updatedCategoryResult = mockMvc.perform(put(BASE + "/categories/{categoryId}", category.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Tablet devices",
                                  "slug":"tablet-devices",
                                  "version":%d
                                }
                                """.formatted(category.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("Tablet devices"))
                .andReturn();
        String updatedCategoryBody = updatedCategoryResult.getResponse().getContentAsString();
        long categoryVersion = readLong(updatedCategoryBody, "$.result.version");

        ProductResult product = createProduct(adminToken, category.id(), "Tablet", "tablet", null);
        MvcResult updatedProductResult = mockMvc.perform(put(BASE + "/products/{productId}", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProductJson(
                                category.id(), "Tablet Pro", "tablet-pro", "Professional tablet", product.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("Tablet Pro"))
                .andExpect(jsonPath("$.result.description").value("Professional tablet"))
                .andReturn();
        ProductResult updatedProduct = toProductResult(updatedProductResult);

        ProductResult withVariant = addVariant(
                adminToken, product.id(), "TABLET-PRO-256", "256 GB", "899.00", "USD", updatedProduct.version());
        UUID variantId = UUID.fromString(JsonPath.read(withVariant.body(), "$.result.variants[0].id"));
        long variantVersion = readLong(withVariant.body(), "$.result.variants[0].version");

        mockMvc.perform(put(BASE + "/products/{productId}/variants/{variantId}", product.id(), variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"256 GB / 12 GB RAM",
                                  "price":929.50,
                                  "currency":"USD",
                                  "version":%d
                                }
                                """.formatted(variantVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.variants[0].name").value("256 GB / 12 GB RAM"))
                .andExpect(jsonPath("$.result.variants[0].price").value(929.50))
                .andExpect(jsonPath("$.result.variants[0].version").value(variantVersion + 1));

        mockMvc.perform(patch(BASE + "/categories/{categoryId}/status", category.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE","version":%d}
                                """.formatted(categoryVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1109));
    }

    @Test
    void enforcesPublishHideAndOptimisticLockingStateTransitions() throws Exception {
        String adminToken = accountWithRole("catalog-state-admin", RoleCode.ADMIN);
        CategoryResult category = createCategory(adminToken, "PHONES", "Phones", "phones");
        ProductResult product = createProduct(adminToken, category.id(), "Phone", "phone", null);

        mockMvc.perform(patch(BASE + "/products/{productId}/publish", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson(product.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1107));

        ProductResult withVariant =
                addVariant(adminToken, product.id(), "PHONE-BLACK", "Black", "499.90", "USD", product.version());
        long variantVersion = readLong(withVariant.body(), "$.result.variants[0].version");
        UUID variantId = UUID.fromString(JsonPath.read(withVariant.body(), "$.result.variants[0].id"));

        ProductResult published =
                changeProductState(adminToken, product.id(), "publish", withVariant.version(), "PUBLISHED");

        mockMvc.perform(patch(BASE + "/products/{productId}/variants/{variantId}/status", product.id(), variantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE","version":%d}
                                """.formatted(variantVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1107));

        ProductResult hidden = changeProductState(adminToken, product.id(), "hide", published.version(), "HIDDEN");

        mockMvc.perform(put(BASE + "/products/{productId}", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProductJson(
                                category.id(), "Stale name", "stale-name", null, published.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1106));

        mockMvc.perform(get(BASE + "/products/{productId}", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("Phone"))
                .andExpect(jsonPath("$.result.status").value("HIDDEN"))
                .andExpect(jsonPath("$.result.version").value(hidden.version()));
    }

    @Test
    void rejectsDuplicateBusinessIdentifiersAndAnInUseCategory() throws Exception {
        String adminToken = accountWithRole("catalog-duplicate-admin", RoleCode.ADMIN);
        CategoryResult category = createCategory(adminToken, "AUDIO", "Audio", "audio");
        ProductResult first = createProduct(adminToken, category.id(), "Headphones", "headphones", null);
        ProductResult second = createProduct(adminToken, category.id(), "Speaker", "speaker", null);
        addVariant(adminToken, first.id(), "AUDIO-SKU-01", "Black", "49.90", "USD", first.version());

        mockMvc.perform(post(BASE + "/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson(category.id(), "Duplicate", "HEADPHONES", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1104));

        mockMvc.perform(post(BASE + "/products/{productId}/variants", second.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variantJson("audio-sku-01", "Duplicate", "59.90", "USD", second.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1105));

        mockMvc.perform(patch(BASE + "/categories/{categoryId}/status", category.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE","version":%d}
                                """.formatted(category.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1109));
    }

    @Test
    void returnsStableAllowlistedPaginationAndEscapesWildcardKeywords() throws Exception {
        String adminToken = accountWithRole("catalog-page-admin", RoleCode.ADMIN);
        CategoryResult category = createCategory(adminToken, "OFFICE", "Office", "office");
        createProduct(adminToken, category.id(), "Beta", "beta", null);
        createProduct(adminToken, category.id(), "Alpha", "alpha", null);
        createProduct(adminToken, category.id(), "Gamma", "gamma", null);

        mockMvc.perform(get(BASE + "/products")
                        .param("categoryId", category.id().toString())
                        .param("sortBy", "NAME")
                        .param("direction", "ASC")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.result.content[1].name").value("Beta"))
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.first").value(true))
                .andExpect(jsonPath("$.result.last").value(false));

        mockMvc.perform(get(BASE + "/products")
                        .param("keyword", "%")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    private String accountWithRole(String username, RoleCode roleCode) throws Exception {
        identityClient.register(username);
        if (roleCode != RoleCode.USER) {
            jdbcTemplate.update("""
                    INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code)
                    SELECT id, ? FROM xac_thuc_nguoi_dung WHERE username = ?
                    """, roleCode.name(), username);
            entityManager.clear();
        }
        return identityClient.authenticate(username).accessToken();
    }

    private CategoryResult createCategory(String token, String code, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(code, name, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new CategoryResult(
                UUID.fromString(JsonPath.read(body, "$.result.id")), readLong(body, "$.result.version"));
    }

    private ProductResult createProduct(String token, UUID categoryId, String name, String slug, String description)
            throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson(categoryId, name, slug, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return toProductResult(result);
    }

    private ProductResult addVariant(
            String token, UUID productId, String sku, String name, String price, String currency, long productVersion)
            throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(variantJson(sku, name, price, currency, productVersion)))
                .andExpect(status().isCreated())
                .andReturn();
        return toProductResult(result);
    }

    private ProductResult changeProductState(
            String token, UUID productId, String action, long version, String expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(patch(BASE + "/products/{productId}/" + action, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionJson(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value(expectedStatus))
                .andReturn();
        return toProductResult(result);
    }

    private ProductResult toProductResult(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new ProductResult(
                UUID.fromString(JsonPath.read(body, "$.result.id")), readLong(body, "$.result.version"), body);
    }

    private long readLong(String body, String path) {
        Number value = JsonPath.read(body, path);
        return value.longValue();
    }

    private String categoryJson(String code, String name, String slug) {
        return """
                {"code":"%s","name":"%s","slug":"%s"}
                """.formatted(code, name, slug);
    }

    private String createProductJson(UUID categoryId, String name, String slug, String description) {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        return """
                {
                  "categoryId":"%s",
                  "name":"%s",
                  "slug":"%s",
                  "description":%s
                }
                """.formatted(categoryId, name, slug, descriptionJson);
    }

    private String updateProductJson(UUID categoryId, String name, String slug, String description, long version) {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        return """
                {
                  "categoryId":"%s",
                  "name":"%s",
                  "slug":"%s",
                  "description":%s,
                  "version":%d
                }
                """.formatted(categoryId, name, slug, descriptionJson, version);
    }

    private String variantJson(String sku, String name, String price, String currency, long productVersion) {
        return """
                {
                  "sku":"%s",
                  "name":"%s",
                  "price":%s,
                  "currency":"%s",
                  "productVersion":%d
                }
                """.formatted(sku, name, price, currency, productVersion);
    }

    private String versionJson(long version) {
        return """
                {"version":%d}
                """.formatted(version);
    }

    private record CategoryResult(UUID id, long version) {}

    private record ProductResult(UUID id, long version, String body) {}
}
