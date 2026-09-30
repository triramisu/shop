package com.shop.catalog.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.dto.request.ProductSortField;
import com.shop.catalog.internal.entity.ProductStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductSearchCriteriaTests {

    @Test
    void normalizesWhitespaceCaseUnicodeAndBuildsSafeSearchExpressions() {
        UUID categoryId = UUID.randomUUID();

        ProductSearchCriteria criteria = ProductSearchCriteria.of(
                "\u00A0  ĐIỆN   THOẠI %_!  \u00A0",
                categoryId, ProductStatus.PUBLISHED, ProductSortField.NAME, CatalogSortDirection.ASC);

        assertThat(criteria.normalizedKeyword()).isEqualTo("điện thoại %_!");
        assertThat(criteria.containsLikePattern()).isEqualTo("%điện thoại !%!_!!%");
        assertThat(criteria.prefixLikePattern()).isEqualTo("điện thoại !%!_!!%");
        assertThat(criteria.fullTextQuery()).isEqualTo("+điện* +thoại*");
        assertThat(criteria.categoryId()).isEqualTo(categoryId);
        assertThat(criteria.hasKeyword()).isTrue();
    }

    @Test
    void treatsBlankAsNoKeywordAndKeepsPunctuationLiteral() {
        ProductSearchCriteria blank = criteria("\u00A0");
        ProductSearchCriteria punctuation = criteria("%_!");

        assertThat(blank.hasKeyword()).isFalse();
        assertThat(blank.containsLikePattern()).isNull();
        assertThat(blank.prefixLikePattern()).isNull();
        assertThat(blank.fullTextQuery()).isNull();
        assertThat(punctuation.hasKeyword()).isTrue();
        assertThat(punctuation.containsLikePattern()).isEqualTo("%!%!_!!%");
        assertThat(punctuation.prefixLikePattern()).isEqualTo("!%!_!!%");
        assertThat(punctuation.fullTextQuery()).isNull();
    }

    @Test
    void removesDuplicateAndShortFullTextTokensWithoutChangingLiteralPattern() {
        ProductSearchCriteria criteria = criteria("TV tv pro pro 01");

        assertThat(criteria.fullTextQuery()).isEqualTo("+pro*");
        assertThat(criteria.prefixLikePattern()).isEqualTo("tv tv pro pro 01%");
    }

    private ProductSearchCriteria criteria(String keyword) {
        return ProductSearchCriteria.of(keyword, null, null, ProductSortField.CREATED_AT, CatalogSortDirection.DESC);
    }
}
