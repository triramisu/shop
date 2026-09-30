package com.shop.catalog;

import static com.shop.identity.support.IdentityApiTestClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shop.catalog.internal.image.storage.memory.InMemoryObjectStorage;
import com.shop.identity.internal.constant.RoleCode;
import com.shop.identity.support.IdentityApiTestClient;
import jakarta.persistence.EntityManager;
import java.awt.Color;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageIntegrationTests {

    private static final String BASE = "/api/admin/catalog";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private InMemoryObjectStorage objectStorage;

    private IdentityApiTestClient identityClient;
    private String testId;

    @BeforeEach
    void setUp() {
        identityClient = new IdentityApiTestClient(mockMvc);
        testId = UUID.randomUUID().toString().substring(0, 8);
        objectStorage.clear();
    }

    @AfterEach
    void cleanUp() {
        objectStorage.clear();
        jdbcTemplate.update("""
                DELETE FROM san_pham_hinh_anh
                 WHERE product_id IN (
                       SELECT product.id
                         FROM san_pham_san_pham product
                         JOIN san_pham_danh_muc category ON category.id = product.category_id
                        WHERE category.code LIKE 'IMAGE_%')
                """);
        jdbcTemplate.update("""
                DELETE FROM san_pham_bien_the
                 WHERE product_id IN (
                       SELECT product.id
                         FROM san_pham_san_pham product
                         JOIN san_pham_danh_muc category ON category.id = product.category_id
                        WHERE category.code LIKE 'IMAGE_%')
                """);
        jdbcTemplate.update("""
                DELETE FROM san_pham_san_pham
                 WHERE category_id IN (SELECT id FROM san_pham_danh_muc WHERE code LIKE 'IMAGE_%')
                """);
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code LIKE 'IMAGE_%'");
        jdbcTemplate.update("""
                DELETE FROM xac_thuc_phien_lam_moi
                 WHERE user_id IN (SELECT id FROM xac_thuc_nguoi_dung WHERE username LIKE 'image-%')
                """);
        jdbcTemplate.update("""
                DELETE FROM xac_thuc_nguoi_dung_vai_tro
                 WHERE user_id IN (SELECT id FROM xac_thuc_nguoi_dung WHERE username LIKE 'image-%')
                """);
        jdbcTemplate.update("DELETE FROM xac_thuc_nguoi_dung WHERE username LIKE 'image-%'");
    }

    @Test
    void uploadsArrangesAndDeletesValidatedImages() throws Exception {
        CatalogContext context = createCatalog("lifecycle", RoleCode.ADMIN);

        MvcResult uploadResult = mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(image("files", "front.png", "image/png", "png"))
                        .file(image("files", "back.jpg", "image/jpeg", "jpg"))
                        .param("primaryIndex", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].primary").value(false))
                .andExpect(jsonPath("$.result[0].displayOrder").value(0))
                .andExpect(jsonPath("$.result[1].primary").value(true))
                .andExpect(jsonPath("$.result[1].contentType").value("image/jpeg"))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        UUID firstImageId = UUID.fromString(JsonPath.read(responseBody, "$.result[0].id"));
        UUID secondImageId = UUID.fromString(JsonPath.read(responseBody, "$.result[1].id"));
        String firstObjectKey = JsonPath.read(responseBody, "$.result[0].objectKey");
        assertThat(firstObjectKey)
                .startsWith("catalog/products/" + context.productId() + "/")
                .endsWith(".png")
                .doesNotContain("front.png");
        assertThat(objectStorage.size()).isEqualTo(2);

        mockMvc.perform(put(BASE + "/products/{productId}/images/arrangement", context.productId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageIds":["%s","%s"],
                                  "primaryImageId":"%s"
                                }
                                """.formatted(secondImageId, firstImageId, firstImageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].id").value(secondImageId.toString()))
                .andExpect(jsonPath("$.result[0].displayOrder").value(0))
                .andExpect(jsonPath("$.result[0].primary").value(false))
                .andExpect(jsonPath("$.result[1].id").value(firstImageId.toString()))
                .andExpect(jsonPath("$.result[1].primary").value(true));

        mockMvc.perform(delete(BASE + "/products/{productId}/images/{imageId}", context.productId(), firstImageId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(secondImageId.toString()))
                .andExpect(jsonPath("$.result[0].primary").value(true))
                .andExpect(jsonPath("$.result[0].displayOrder").value(0));

        assertThat(objectStorage.size()).isEqualTo(1);
        assertThat(imageRowCount(context.productId())).isEqualTo(1);
        mockMvc.perform(get(BASE + "/products/{productId}/images", context.productId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].originalFilename").value("back.jpg"));
    }

    @Test
    void rejectsSpoofedCorruptOversizedAndExcessiveUploads() throws Exception {
        CatalogContext context = createCatalog("validation", RoleCode.ADMIN);
        byte[] png = imageBytes("png");

        mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(new MockMultipartFile("files", "spoofed.jpg", "image/jpeg", png))
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(1131));

        mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(new MockMultipartFile("files", "corrupt.png", "image/png", new byte[] {
                            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
                        }))
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1132));

        mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(new MockMultipartFile("files", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value(1130));

        MockMultipartHttpServletRequestBuilder tooMany =
                multipart(BASE + "/products/{productId}/images", context.productId());
        for (int index = 0; index < 6; index++) {
            tooMany.file(image("files", "image-" + index + ".png", "image/png", "png"));
        }
        mockMvc.perform(tooMany.header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1129));

        assertThat(objectStorage.size()).isZero();
        assertThat(imageRowCount(context.productId())).isZero();
    }

    @Test
    void enforcesPerProductLimitAndAdministratorPermission() throws Exception {
        CatalogContext context = createCatalog("limit", RoleCode.ADMIN);
        String customerToken = createAccount("customer", RoleCode.USER);

        MockMultipartHttpServletRequestBuilder initialUpload =
                multipart(BASE + "/products/{productId}/images", context.productId());
        for (int index = 0; index < 5; index++) {
            initialUpload.file(image("files", "first-" + index + ".png", "image/png", "png"));
        }
        mockMvc.perform(initialUpload.header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isCreated());

        MockMultipartHttpServletRequestBuilder secondUpload =
                multipart(BASE + "/products/{productId}/images", context.productId());
        for (int index = 0; index < 5; index++) {
            secondUpload.file(image("files", "second-" + index + ".jpg", "image/jpeg", "jpg"));
        }
        mockMvc.perform(secondUpload.header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(image("files", "overflow.png", "image/png", "png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1133));

        mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(image("files", "denied.png", "image/png", "png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));

        assertThat(objectStorage.size()).isEqualTo(10);
        assertThat(imageRowCount(context.productId())).isEqualTo(10);
    }

    @Test
    void rollsBackStoredObjectsAndMetadataWhenStorageFailsMidUpload() throws Exception {
        CatalogContext context = createCatalog("rollback", RoleCode.ADMIN);
        objectStorage.failAfterSuccessfulStores(1);

        mockMvc.perform(multipart(BASE + "/products/{productId}/images", context.productId())
                        .file(image("files", "first.png", "image/png", "png"))
                        .file(image("files", "second.jpg", "image/jpeg", "jpg"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(context.token())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(1136));

        assertThat(objectStorage.size()).isZero();
        assertThat(imageRowCount(context.productId())).isZero();
    }

    private CatalogContext createCatalog(String purpose, RoleCode roleCode) throws Exception {
        String token = createAccount(purpose, roleCode);
        String suffix = purpose.toUpperCase(java.util.Locale.ROOT) + "_" + testId.toUpperCase(java.util.Locale.ROOT);

        MvcResult categoryResult = mockMvc.perform(post(BASE + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"IMAGE_%s","name":"Image %s","slug":"image-%s"}
                                """.formatted(suffix, purpose, purpose + "-" + testId)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID categoryId =
                UUID.fromString(JsonPath.read(categoryResult.getResponse().getContentAsString(), "$.result.id"));

        MvcResult productResult = mockMvc.perform(post(BASE + "/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"%s",
                                  "name":"Image product %s",
                                  "slug":"image-product-%s"
                                }
                                """.formatted(categoryId, purpose, purpose + "-" + testId)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID productId =
                UUID.fromString(JsonPath.read(productResult.getResponse().getContentAsString(), "$.result.id"));
        return new CatalogContext(token, productId);
    }

    private String createAccount(String purpose, RoleCode roleCode) throws Exception {
        String username = "image-" + purpose + "-" + testId;
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

    private MockMultipartFile image(String field, String filename, String contentType, String format) throws Exception {
        return new MockMultipartFile(field, filename, contentType, imageBytes(format));
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }

    private int imageRowCount(UUID productId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM san_pham_hinh_anh WHERE product_id = ?", Integer.class, productId);
        return count == null ? 0 : count;
    }

    private record CatalogContext(String token, UUID productId) {}
}
