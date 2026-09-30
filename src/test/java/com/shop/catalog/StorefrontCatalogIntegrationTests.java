package com.shop.catalog;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.image.storage.memory.InMemoryObjectStorage;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.support.IdentityApiTestClient;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontCatalogIntegrationTests {

    private static final String PUBLIC_BASE = "/api/catalog";
    private static final String ADMIN_BASE = "/api/admin/catalog";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InMemoryObjectStorage objectStorage;

    private IdentityApiTestClient identityClient;

    @BeforeEach
    void setUp() {
        identityClient = new IdentityApiTestClient(mockMvc);
        objectStorage.clear();
    }

    @AfterEach
    void cleanUpObjectStorage() {
        objectStorage.clear();
    }

    @Test
    void anonymousStorefrontExposesOnlyPublishedSellableDataAndSafeFields() throws Exception {
        String adminToken = adminToken();
        CategoryResult activeCategory = createCategory(adminToken, "PUBLIC_ACTIVE", "Public active", "public-active");
        CategoryResult inactiveCategory =
                createCategory(adminToken, "PUBLIC_INACTIVE", "Public inactive", "public-inactive");
        mockMvc.perform(patch(ADMIN_BASE + "/categories/{categoryId}/status", inactiveCategory.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE","version":%d}
                                """.formatted(inactiveCategory.version())))
                .andExpect(status().isOk());

        ProductResult published =
                createProduct(adminToken, activeCategory.id(), "Public phone", "public-phone", "A published phone");
        published = addVariant(adminToken, published, "PUBLIC-PHONE-A", "Active variant", "499.90");
        ProductResult withTwoVariants = addVariant(adminToken, published, "PUBLIC-PHONE-B", "Hidden variant", "599.90");
        UUID hiddenVariantId = UUID.fromString(JsonPath.read(withTwoVariants.body(), "$.result.variants[1].id"));
        long hiddenVariantVersion =
                number(withTwoVariants.body(), "$.result.variants[1].version").longValue();
        MvcResult inactiveVariantResult = mockMvc.perform(patch(
                                ADMIN_BASE + "/products/{productId}/variants/{variantId}/status",
                                withTwoVariants.id(),
                                hiddenVariantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE","version":%d}
                                """.formatted(hiddenVariantVersion)))
                .andExpect(status().isOk())
                .andReturn();
        ProductResult readyToPublish = productResult(inactiveVariantResult);
        ProductResult publishedProduct = changeProductState(adminToken, readyToPublish, "publish");

        ProductResult draft =
                createProduct(adminToken, activeCategory.id(), "Draft phone", "draft-phone", "Must stay private");
        uploadImage(adminToken, publishedProduct.id(), "published.png");
        uploadImage(adminToken, draft.id(), "draft.png");

        mockMvc.perform(get(PUBLIC_BASE + "/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].name").value("Public active"))
                .andExpect(jsonPath("$.result[0].code").doesNotExist())
                .andExpect(jsonPath("$.result[0].status").doesNotExist())
                .andExpect(jsonPath("$.result[0].version").doesNotExist());

        mockMvc.perform(get(PUBLIC_BASE + "/products")
                        .param("categoryId", activeCategory.id().toString())
                        .param("status", "DRAFT")
                        .param("sortBy", "NAME")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].id")
                        .value(publishedProduct.id().toString()))
                .andExpect(jsonPath("$.result.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].version").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].category.code").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].variants.length()").value(1))
                .andExpect(jsonPath("$.result.content[0].variants[0].sku").value("PUBLIC-PHONE-A"))
                .andExpect(jsonPath("$.result.content[0].variants[0].status").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].variants[0].version").doesNotExist());

        mockMvc.perform(get(PUBLIC_BASE + "/products/{productId}", draft.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1101));
        mockMvc.perform(get(PUBLIC_BASE + "/products/{productId}", publishedProduct.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(publishedProduct.id().toString()));

        mockMvc.perform(get(PUBLIC_BASE + "/products/{productId}/images", draft.id()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PUBLIC_BASE + "/products/{productId}/images", publishedProduct.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.result[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.result[0].originalFilename").doesNotExist())
                .andExpect(jsonPath("$.result[0].version").doesNotExist());
    }

    @Test
    void validatesPublicPaginationAndKeepsAdministrationClosed() throws Exception {
        mockMvc.perform(get(PUBLIC_BASE + "/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1049));
        mockMvc.perform(get(PUBLIC_BASE + "/products").param("sortBy", "PRICE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1010));
        mockMvc.perform(get(ADMIN_BASE + "/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));
    }

    private String adminToken() throws Exception {
        String username = "storefront-admin";
        identityClient.register(username);
        jdbcTemplate.update("""
                INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code)
                SELECT id, ? FROM xac_thuc_nguoi_dung WHERE username = ?
                """, RoleCode.ADMIN.name(), username);
        entityManager.clear();
        return identityClient.authenticate(username).accessToken();
    }

    private CategoryResult createCategory(String token, String code, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(ADMIN_BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","slug":"%s"}
                                """.formatted(code, name, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new CategoryResult(
                UUID.fromString(JsonPath.read(body, "$.result.id")),
                number(body, "$.result.version").longValue());
    }

    private ProductResult createProduct(String token, UUID categoryId, String name, String slug, String description)
            throws Exception {
        MvcResult result = mockMvc.perform(post(ADMIN_BASE + "/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"%s",
                                  "name":"%s",
                                  "slug":"%s",
                                  "description":"%s"
                                }
                                """.formatted(categoryId, name, slug, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return productResult(result);
    }

    private ProductResult addVariant(String token, ProductResult product, String sku, String name, String price)
            throws Exception {
        MvcResult result = mockMvc.perform(post(ADMIN_BASE + "/products/{productId}/variants", product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku":"%s",
                                  "name":"%s",
                                  "price":%s,
                                  "currency":"USD",
                                  "productVersion":%d
                                }
                                """.formatted(sku, name, price, product.version())))
                .andExpect(status().isCreated())
                .andReturn();
        return productResult(result);
    }

    private ProductResult changeProductState(String token, ProductResult product, String action) throws Exception {
        MvcResult result = mockMvc.perform(patch(ADMIN_BASE + "/products/{productId}/" + action, product.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d}
                                """.formatted(product.version())))
                .andExpect(status().isOk())
                .andReturn();
        return productResult(result);
    }

    private void uploadImage(String token, UUID productId, String filename) throws Exception {
        mockMvc.perform(multipart(ADMIN_BASE + "/products/{productId}/images", productId)
                        .file(validPng(filename))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated());
    }

    private MockMultipartFile validPng(String filename) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return new MockMultipartFile("files", filename, "image/png", output.toByteArray());
        }
    }

    private ProductResult productResult(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return new ProductResult(
                UUID.fromString(JsonPath.read(body, "$.result.id")),
                number(body, "$.result.version").longValue(),
                body);
    }

    private Number number(String body, String path) {
        return JsonPath.read(body, path);
    }

    private record CategoryResult(UUID id, long version) {}

    private record ProductResult(UUID id, long version, String body) {}
}
