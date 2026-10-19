package com.shop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.shop.order.internal.checkout.service.OrderCreationService;
import com.shop.order.internal.checkout.service.OrderSnapshotQueryService;
import com.shop.order.internal.dto.request.AddCartItemRequest;
import com.shop.order.internal.dto.response.CartResponse;
import com.shop.order.internal.service.CartService;
import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {"app.order.checkout.pricing.discount-rate=0.10", "app.order.checkout.pricing.tax-rate=0.08"})
@ActiveProfiles("test")
class OrderSnapshotIntegrationTests {

    private static final String OWNER = "snapshot-customer";
    private static final String SKU = "SNAPSHOT-SKU-01";

    @Autowired
    private CatalogCategoryService categoryService;

    @Autowired
    private CatalogProductService productService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private OrderSnapshotQueryService orderSnapshotQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanCommittedData() {
        jdbcTemplate.update("DELETE FROM don_hang_don_dat_hang");
        jdbcTemplate.update("DELETE FROM don_hang_muc_gio_hang");
        jdbcTemplate.update("DELETE FROM don_hang_gio_hang");
        jdbcTemplate.update("DELETE FROM san_pham_bien_the WHERE sku = ?", SKU);
        jdbcTemplate.update("DELETE FROM san_pham_san_pham WHERE slug = 'snapshot-product'");
        jdbcTemplate.update("DELETE FROM san_pham_danh_muc WHERE code = 'SNAPSHOT_CATEGORY'");
    }

    @Test
    void catalogChangesAfterOrderCreationNeverRewriteThePersistedSnapshot() {
        CategoryResponse category = categoryService.create(CreateCategoryRequest.builder()
                .code("SNAPSHOT_CATEGORY")
                .name("Snapshot category")
                .slug("snapshot-category")
                .build());
        ProductResponse product = productService.create(CreateProductRequest.builder()
                .categoryId(category.getId())
                .name("Snapshot product")
                .slug("snapshot-product")
                .build());
        product = productService.addVariant(
                product.getId(),
                CreateProductVariantRequest.builder()
                        .sku(SKU)
                        .name("Tên tại thời điểm mua")
                        .price(new BigDecimal("12.34"))
                        .currency("USD")
                        .productVersion(product.getVersion())
                        .build());
        product = productService.publish(
                product.getId(),
                VersionedCatalogRequest.builder().version(product.getVersion()).build());
        ProductVariantResponse originalVariant = product.getVariants().getFirst();
        CartResponse cart = cartService.addItem(
                OWNER, AddCartItemRequest.builder().sku(SKU).quantity(2).build());

        UUID orderId = orderCreationService.createFromCart(OWNER, cart.getVersion());

        assertThatThrownBy(() -> orderSnapshotQueryService.getOwnedOrder("another-customer", orderId))
                .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));

        ProductResponse updatedProduct = productService.updateVariant(
                product.getId(),
                originalVariant.getId(),
                UpdateProductVariantRequest.builder()
                        .name("Tên mới không được ghi đè snapshot")
                        .price(new BigDecimal("99.99"))
                        .currency("USD")
                        .version(originalVariant.getVersion())
                        .build());
        productService.hide(
                updatedProduct.getId(),
                VersionedCatalogRequest.builder()
                        .version(updatedProduct.getVersion())
                        .build());

        var reloaded = orderSnapshotQueryService.getOwnedOrder(OWNER, orderId);

        assertThat(reloaded.getCurrency()).isEqualTo("USD");
        assertThat(reloaded.getSubtotal()).isEqualByComparingTo("24.6800");
        assertThat(reloaded.getDiscount()).isEqualByComparingTo("2.4700");
        assertThat(reloaded.getTax()).isEqualByComparingTo("1.7800");
        assertThat(reloaded.getGrandTotal()).isEqualByComparingTo("23.9900");
        assertThat(reloaded.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductVariantId()).isEqualTo(originalVariant.getId());
            assertThat(item.getSku()).isEqualTo(SKU);
            assertThat(item.getProductName()).isEqualTo("Tên tại thời điểm mua");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("12.3400");
            assertThat(item.getTotal()).isEqualByComparingTo("23.9900");
        });
    }
}
