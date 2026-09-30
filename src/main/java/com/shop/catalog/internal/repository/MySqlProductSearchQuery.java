package com.shop.catalog.internal.repository;

import com.shop.catalog.internal.dto.request.CatalogSortDirection;
import com.shop.catalog.internal.entity.Product;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        prefix = "app.catalog.search",
        name = "strategy",
        havingValue = "mysql-fulltext",
        matchIfMissing = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MySqlProductSearchQuery implements ProductSearchQuery {

    private static final String CANDIDATE_COLUMNS = "p.id, p.name, p.created_at, p.updated_at";

    NamedParameterJdbcTemplate jdbcTemplate;
    ProductRepository productRepository;

    @Override
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        MapSqlParameterSource parameters = parameters(criteria);
        String candidates = candidateQuery(criteria);
        String pageSql = "SELECT candidate.id FROM (" + candidates + ") candidate ORDER BY " + sortClause(criteria)
                + " LIMIT :limit OFFSET :offset";
        parameters.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());

        List<UUID> ids = jdbcTemplate.query(pageSql, parameters, (resultSet, rowNumber) -> readUuid(resultSet));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + candidates + ") candidate", parameters, Long.class);
        return new PageImpl<>(loadInOrder(ids), pageable, total == null ? 0 : total);
    }

    private MapSqlParameterSource parameters(ProductSearchCriteria criteria) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (criteria.categoryId() != null) {
            parameters.addValue("categoryId", toBytes(criteria.categoryId()));
        }
        if (criteria.status() != null) {
            parameters.addValue("status", criteria.status().name());
        }
        if (criteria.hasKeyword()) {
            parameters.addValue("prefixPattern", criteria.prefixLikePattern());
        }
        if (criteria.fullTextQuery() != null) {
            parameters.addValue("fullTextQuery", criteria.fullTextQuery());
        }
        return parameters;
    }

    private String candidateQuery(ProductSearchCriteria criteria) {
        String filters = filters(criteria);
        if (!criteria.hasKeyword()) {
            return "SELECT " + CANDIDATE_COLUMNS + " FROM san_pham_san_pham p WHERE " + filters;
        }

        List<String> branches = new ArrayList<>();
        if (criteria.fullTextQuery() != null) {
            branches.add("SELECT " + CANDIDATE_COLUMNS + " FROM san_pham_san_pham p WHERE " + filters
                    + " AND MATCH(p.name, p.slug) AGAINST (:fullTextQuery IN BOOLEAN MODE)");
        }
        branches.add("SELECT " + CANDIDATE_COLUMNS + " FROM san_pham_san_pham p WHERE " + filters
                + " AND p.name LIKE :prefixPattern ESCAPE '!'");
        branches.add("SELECT " + CANDIDATE_COLUMNS + " FROM san_pham_san_pham p WHERE " + filters
                + " AND p.slug LIKE :prefixPattern ESCAPE '!'");
        branches.add("SELECT " + CANDIDATE_COLUMNS
                + " FROM san_pham_bien_the v JOIN san_pham_san_pham p ON p.id = v.product_id WHERE " + filters
                + " AND v.deleted_at IS NULL AND v.sku LIKE :prefixPattern ESCAPE '!'");
        return String.join(" UNION ", branches);
    }

    private String filters(ProductSearchCriteria criteria) {
        StringBuilder filters = new StringBuilder("p.deleted_at IS NULL");
        if (criteria.categoryId() != null) {
            filters.append(" AND p.category_id = :categoryId");
        }
        if (criteria.status() != null) {
            filters.append(" AND p.status = :status");
        }
        return filters.toString();
    }

    private String sortClause(ProductSearchCriteria criteria) {
        String column =
                switch (criteria.sortBy()) {
                    case CREATED_AT -> "candidate.created_at";
                    case UPDATED_AT -> "candidate.updated_at";
                    case NAME -> "candidate.name";
                };
        String direction = criteria.direction() == CatalogSortDirection.ASC ? "ASC" : "DESC";
        return column + " " + direction + ", candidate.id ASC";
    }

    private List<Product> loadInOrder(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Product> productsById = productRepository.findAllDetailedByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().map(productsById::get).toList();
    }

    private UUID readUuid(ResultSet resultSet) throws SQLException {
        byte[] bytes = resultSet.getBytes("id");
        if (bytes == null || bytes.length != 16) {
            throw new SQLException("Product search returned an invalid binary UUID");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private byte[] toBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
