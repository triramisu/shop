package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.dto.request.ProductSortField;
import com.shop.catalog.internal.entity.ProductStatus;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ProductSearchCriteria(
        String normalizedKeyword,
        String containsLikePattern,
        String prefixLikePattern,
        String fullTextQuery,
        UUID categoryId,
        ProductStatus status,
        ProductSortField sortBy,
        CatalogSortDirection direction) {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern FULL_TEXT_TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]{3,}");

    public static ProductSearchCriteria of(
            String keyword,
            UUID categoryId,
            ProductStatus status,
            ProductSortField sortBy,
            CatalogSortDirection direction) {
        String normalizedKeyword = normalize(keyword);
        String escapedKeyword = normalizedKeyword == null ? null : escapeLike(normalizedKeyword);
        return new ProductSearchCriteria(
                normalizedKeyword,
                escapedKeyword == null ? null : "%" + escapedKeyword + "%",
                escapedKeyword == null ? null : escapedKeyword + "%",
                toBooleanFullTextQuery(normalizedKeyword),
                categoryId,
                status,
                sortBy,
                direction);
    }

    public boolean hasKeyword() {
        return normalizedKeyword != null;
    }

    private static String normalize(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFKC).strip();
        if (normalized.isBlank()) {
            return null;
        }
        return WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String toBooleanFullTextQuery(String keyword) {
        if (keyword == null) {
            return null;
        }
        Matcher matcher = FULL_TEXT_TOKEN_PATTERN.matcher(keyword);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens.isEmpty()
                ? null
                : tokens.stream()
                        .map(token -> "+" + token + "*")
                        .reduce((left, right) -> left + " " + right)
                        .orElse(null);
    }
}
