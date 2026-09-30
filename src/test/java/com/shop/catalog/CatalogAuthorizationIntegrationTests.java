package com.shop.catalog;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.math.BigDecimal;
import java.nio.ByteBuffer;
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
class CatalogAuthorizationIntegrationTests {

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
    void authorizesAdministrationByCatalogPermissionsInsteadOfHardCodedRoles() throws Exception {
        String userToken = accountWithRole("catalog-auth-user", RoleCode.USER);
        String staffToken = accountWithRole("catalog-auth-staff", RoleCode.STAFF);
        String writerToken = accountWithPermissions("catalog-auth-writer", "CATALOG_TEST_WRITER", "CATALOG_WRITE");
        String readerToken = accountWithPermissions("catalog-auth-reader", "CATALOG_TEST_READER", "CATALOG_READ");

        mockMvc.perform(post(ADMIN_BASE + "/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("NO_TOKEN", "No token", "no-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006));

        mockMvc.perform(post(ADMIN_BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("USER_DENIED", "User denied", "user-denied")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        createCategory(staffToken, "STAFF_ALLOWED", "Staff allowed", "staff-allowed");
        createCategory(writerToken, "WRITER_ALLOWED", "Writer allowed", "writer-allowed");

        mockMvc.perform(get(ADMIN_BASE + "/categories").header(HttpHeaders.AUTHORIZATION, bearer(writerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_BASE + "/categories").header(HttpHeaders.AUTHORIZATION, bearer(readerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(readerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("READER_DENIED", "Reader denied", "reader-denied")))
                .andExpect(status().isForbidden());

        assertThat(categoryCount("USER_DENIED")).isZero();
        assertThat(categoryCount("READER_DENIED")).isZero();
        assertThat(categoryCount("STAFF_ALLOWED")).isEqualTo(1);
        assertThat(categoryCount("WRITER_ALLOWED")).isEqualTo(1);
    }

    @Test
    void keepsWritePublishAndImagePermissionsIndependent() throws Exception {
        String writerToken = accountWithPermissions("catalog-flow-writer", "CATALOG_FLOW_WRITER", "CATALOG_WRITE");
        String publisherToken =
                accountWithPermissions("catalog-flow-publisher", "CATALOG_FLOW_PUBLISHER", "CATALOG_PUBLISH");
        String imageToken = accountWithPermissions("catalog-flow-image", "CATALOG_FLOW_IMAGE", "CATALOG_IMAGE_MANAGE");

        UUID categoryId = createCategory(writerToken, "FLOW_CATEGORY", "Flow category", "flow-category");
        MvcResult productResult = mockMvc.perform(post(ADMIN_BASE + "/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(writerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"%s",
                                  "name":"Permission product",
                                  "slug":"permission-product"
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        String productBody = productResult.getResponse().getContentAsString();
        UUID productId = UUID.fromString(JsonPath.read(productBody, "$.result.id"));
        long productVersion = number(productBody, "$.result.version").longValue();

        MvcResult variantResult = mockMvc.perform(post(ADMIN_BASE + "/products/{productId}/variants", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(writerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku":"PERMISSION-SKU-01",
                                  "name":"Default",
                                  "price":%s,
                                  "currency":"USD",
                                  "productVersion":%d
                                }
                                """.formatted(new BigDecimal("29.90"), productVersion)))
                .andExpect(status().isCreated())
                .andReturn();
        long publishVersion = number(variantResult.getResponse().getContentAsString(), "$.result.version")
                .longValue();

        mockMvc.perform(patch(ADMIN_BASE + "/products/{productId}/publish", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(writerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d}
                                """.formatted(publishVersion)))
                .andExpect(status().isForbidden());
        assertThat(productStatus(productId)).isEqualTo("DRAFT");

        mockMvc.perform(patch(ADMIN_BASE + "/products/{productId}/publish", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(publisherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d}
                                """.formatted(publishVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PUBLISHED"));

        mockMvc.perform(multipart(ADMIN_BASE + "/products/{productId}/images", productId)
                        .file(validPng())
                        .header(HttpHeaders.AUTHORIZATION, bearer(imageToken)))
                .andExpect(status().isCreated());
        mockMvc.perform(get(ADMIN_BASE + "/products/{productId}/images", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(imageToken)))
                .andExpect(status().isForbidden());

        assertThat(objectStorage.size()).isEqualTo(1);
        assertThat(productStatus(productId)).isEqualTo("PUBLISHED");
    }

    private String accountWithRole(String username, RoleCode roleCode) throws Exception {
        identityClient.register(username);
        if (roleCode != RoleCode.USER) {
            assignRole(username, roleCode.name());
        }
        return identityClient.authenticate(username).accessToken();
    }

    private String accountWithPermissions(String username, String roleCode, String... permissions) throws Exception {
        identityClient.register(username);
        jdbcTemplate.update(
                "INSERT INTO xac_thuc_vai_tro (code, description, system_role, version) VALUES (?, ?, FALSE, 0)",
                roleCode,
                "Test role " + roleCode);
        for (String permission : permissions) {
            jdbcTemplate.update(
                    "INSERT INTO xac_thuc_vai_tro_quyen_han (role_code, permission_code) VALUES (?, ?)",
                    roleCode,
                    permission);
        }
        assignRole(username, roleCode);
        return identityClient.authenticate(username).accessToken();
    }

    private void assignRole(String username, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO xac_thuc_nguoi_dung_vai_tro (user_id, role_code)
                SELECT id, ? FROM xac_thuc_nguoi_dung WHERE username = ?
                """, roleCode, username);
        entityManager.clear();
    }

    private UUID createCategory(String token, String code, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(ADMIN_BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(code, name, slug)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.result.id"));
    }

    private String categoryJson(String code, String name, String slug) {
        return """
                {"code":"%s","name":"%s","slug":"%s"}
                """.formatted(code, name, slug);
    }

    private long categoryCount(String code) {
        Long count =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM san_pham_danh_muc WHERE code = ?", Long.class, code);
        return count == null ? 0 : count;
    }

    private String productStatus(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM san_pham_san_pham WHERE id = ?", String.class, uuidBytes(productId));
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private Number number(String body, String path) {
        return JsonPath.read(body, path);
    }

    private MockMultipartFile validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return new MockMultipartFile("files", "permission.png", "image/png", output.toByteArray());
        }
    }
}
