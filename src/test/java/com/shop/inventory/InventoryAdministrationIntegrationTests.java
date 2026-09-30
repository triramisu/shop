package com.shop.inventory;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.dto.request.CreateCategoryRequest;
import com.shop.catalog.internal.dto.request.CreateProductRequest;
import com.shop.catalog.internal.dto.request.CreateProductVariantRequest;
import com.shop.catalog.internal.dto.response.CategoryResponse;
import com.shop.catalog.internal.dto.response.ProductResponse;
import com.shop.catalog.internal.service.CatalogCategoryService;
import com.shop.catalog.internal.service.CatalogProductService;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.support.IdentityApiTestClient;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
class InventoryAdministrationIntegrationTests {

    private static final String BASE = "/api/admin/inventory";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CatalogCategoryService catalogCategoryService;

    @Autowired
    private CatalogProductService catalogProductService;

    private IdentityApiTestClient identityClient;

    @BeforeEach
    void setUpIdentityClient() {
        identityClient = new IdentityApiTestClient(mockMvc);
    }

    @Test
    void protectsEndpointsAndCreatesStockFromThePublishedCatalogContract() throws Exception {
        String sku = createCatalogSku("AUTH", "INVENTORY-AUTH-01");
        String customerToken = accountWithRole("inventory-customer", RoleCode.USER);
        String staffToken = accountWithRole("inventory-staff", RoleCode.STAFF);

        mockMvc.perform(get(BASE + "/stock-items")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/stock-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStockJson(sku, "WAREHOUSE_01", 10, "Phiếu nhập đầu kỳ", "RECEIPT-001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        MvcResult created = mockMvc.perform(post(BASE + "/stock-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStockJson(sku, "warehouse_01", 10, "Phiếu nhập đầu kỳ", "RECEIPT-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.sku").value(sku))
                .andExpect(jsonPath("$.result.locationCode").value("WAREHOUSE_01"))
                .andExpect(jsonPath("$.result.onHand").value(10))
                .andExpect(jsonPath("$.result.reserved").value(0))
                .andExpect(jsonPath("$.result.available").value(10))
                .andExpect(jsonPath("$.result.product").doesNotExist())
                .andReturn();
        UUID stockItemId = readUuid(created, "$.result.id");

        mockMvc.perform(get(BASE + "/stock-items/{stockItemId}/movements", stockItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].movementType").value("INITIAL"))
                .andExpect(jsonPath("$.result.content[0].onHandDelta").value(10))
                .andExpect(jsonPath("$.result.content[0].onHandAfter").value(10))
                .andExpect(jsonPath("$.result.content[0].referenceId").value("RECEIPT-001"));
    }

    @Test
    void adjustsStockWithOptimisticVersionAndRollsBackInvalidBalances() throws Exception {
        String staffToken = accountWithRole("inventory-adjust-staff", RoleCode.STAFF);
        String sku = createCatalogSku("ADJUST", "INVENTORY-ADJUST-01");
        StockResult stock = createStock(staffToken, sku, "WAREHOUSE_02", 10);

        MvcResult adjusted = mockMvc.perform(post(BASE + "/stock-items/{stockItemId}/adjustments", stock.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentJson(-4, "Kiểm kê thực tế", "COUNT-001", stock.version())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.onHand").value(6))
                .andExpect(jsonPath("$.result.available").value(6))
                .andExpect(jsonPath("$.result.version").value(stock.version() + 1))
                .andReturn();
        long adjustedVersion = readLong(adjusted, "$.result.version");

        mockMvc.perform(post(BASE + "/stock-items/{stockItemId}/adjustments", stock.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentJson(1, "Phiên bản cũ", null, stock.version())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1203));

        mockMvc.perform(post(BASE + "/stock-items/{stockItemId}/adjustments", stock.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentJson(-7, "Không được âm", null, adjustedVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1204));

        mockMvc.perform(get(BASE + "/stock-items/{stockItemId}", stock.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.onHand").value(6))
                .andExpect(jsonPath("$.result.version").value(adjustedVersion));
        mockMvc.perform(get(BASE + "/stock-items/{stockItemId}/movements", stock.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.content[*].movementType").value(contains("ADJUSTMENT", "INITIAL")));
    }

    @Test
    void validatesCatalogReferenceUniquenessPaginationAndLiteralSearch() throws Exception {
        String staffToken = accountWithRole("inventory-search-staff", RoleCode.STAFF);
        String alphaSku = createCatalogSku("SEARCH_A", "INVENTORY-ALPHA_01");
        String betaSku = createCatalogSku("SEARCH_B", "INVENTORY-BETA-01");
        createStock(staffToken, betaSku, "WAREHOUSE_03", 5);
        createStock(staffToken, alphaSku, "WAREHOUSE_03", 8);

        mockMvc.perform(get(BASE + "/stock-items")
                        .param("locationCode", "warehouse_03")
                        .param("sortBy", "SKU")
                        .param("direction", "ASC")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].sku").value(alphaSku))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2));

        mockMvc.perform(get(BASE + "/stock-items")
                        .param("keyword", "ALPHA_")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].sku").value(alphaSku));

        mockMvc.perform(post(BASE + "/stock-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStockJson(alphaSku, "WAREHOUSE_03", 1, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1202));
        mockMvc.perform(post(BASE + "/stock-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStockJson("UNKNOWN-SKU", "WAREHOUSE_03", 1, null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1201));
        mockMvc.perform(get(BASE + "/stock-items")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1048));
        mockMvc.perform(post(BASE + "/stock-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"INVENTORY-ALPHA_01","locationCode":"WAREHOUSE_04"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1216));
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

    private String createCatalogSku(String suffix, String sku) {
        String slugSuffix = suffix.toLowerCase().replace('_', '-');
        CategoryResponse category = catalogCategoryService.create(CreateCategoryRequest.builder()
                .code("INV_" + suffix)
                .name("Inventory " + suffix)
                .slug("inventory-" + slugSuffix)
                .build());
        ProductResponse product = catalogProductService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Product " + suffix)
                .slug("product-" + slugSuffix)
                .build());
        catalogProductService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku(sku)
                        .name("Default")
                        .price(new BigDecimal("99.90"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        return sku;
    }

    private StockResult createStock(String token, String sku, String location, long quantity) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE + "/stock-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStockJson(sku, location, quantity, "Tồn đầu kỳ", null)))
                .andExpect(status().isCreated())
                .andReturn();
        return new StockResult(readUuid(result, "$.result.id"), readLong(result, "$.result.version"));
    }

    private String createStockJson(String sku, String location, long quantity, String reason, String referenceId) {
        return """
                {
                  "sku":"%s",
                  "locationCode":"%s",
                  "initialQuantity":%d,
                  "reason":%s,
                  "referenceId":%s
                }
                """.formatted(sku, location, quantity, jsonString(reason), jsonString(referenceId));
    }

    private String adjustmentJson(long delta, String reason, String referenceId, long version) {
        return """
                {
                  "quantityDelta":%d,
                  "reason":"%s",
                  "referenceId":%s,
                  "version":%d
                }
                """.formatted(delta, reason, jsonString(referenceId), version);
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private UUID readUuid(MvcResult result, String path) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), path));
    }

    private long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private record StockResult(UUID id, long version) {}
}
