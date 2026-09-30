-- Chỉ chạy trên MySQL local/disposable. Script tạo rồi xóa các bảng benchmark độc lập.
DROP TABLE IF EXISTS benchmark_san_pham_bien_the;
DROP TABLE IF EXISTS benchmark_san_pham_san_pham;
DROP TABLE IF EXISTS benchmark_digits;

CREATE TABLE benchmark_san_pham_san_pham (
    id BINARY(16) NOT NULL,
    category_id BINARY(16) NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deleted_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_benchmark_san_pham_slug (slug),
    FULLTEXT KEY ft_benchmark_san_pham_tim_kiem (name, slug),
    KEY idx_benchmark_san_pham_hien_hanh_tao (deleted_at, created_at, id),
    KEY idx_benchmark_san_pham_hien_hanh_cap_nhat (deleted_at, updated_at, id),
    KEY idx_benchmark_san_pham_hien_hanh_ten (deleted_at, name, id),
    KEY idx_benchmark_san_pham_danh_muc_trang_thai_tao
        (category_id, status, deleted_at, created_at, id)
);

CREATE TABLE benchmark_san_pham_bien_the (
    id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    deleted_at TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_benchmark_bien_the_sku (sku),
    KEY idx_benchmark_bien_the_product (product_id, deleted_at)
);

CREATE TABLE benchmark_digits (number TINYINT NOT NULL PRIMARY KEY);
INSERT INTO benchmark_digits (number) VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

INSERT INTO benchmark_san_pham_san_pham
    (id, category_id, name, slug, status, deleted_at, created_at, updated_at)
SELECT
    UNHEX(MD5(CONCAT('product-', sequence_number))),
    UNHEX(MD5(CONCAT('category-', MOD(sequence_number, 20)))),
    CASE
        WHEN MOD(sequence_number, 10) = 0
            THEN CONCAT('Wireless Mechanical Keyboard ', LPAD(sequence_number, 5, '0'))
        ELSE CONCAT('Benchmark Product ', LPAD(sequence_number, 5, '0'))
    END,
    CASE
        WHEN MOD(sequence_number, 10) = 0
            THEN CONCAT('wireless-mechanical-keyboard-', LPAD(sequence_number, 5, '0'))
        ELSE CONCAT('benchmark-product-', LPAD(sequence_number, 5, '0'))
    END,
    CASE MOD(sequence_number, 3)
        WHEN 0 THEN 'PUBLISHED'
        WHEN 1 THEN 'DRAFT'
        ELSE 'HIDDEN'
    END,
    NULL,
    TIMESTAMP('2026-01-01 00:00:00') + INTERVAL sequence_number SECOND,
    TIMESTAMP('2026-01-01 00:00:00') + INTERVAL sequence_number SECOND
FROM (
    SELECT d0.number
         + d1.number * 10
         + d2.number * 100
         + d3.number * 1000
         + d4.number * 10000 AS sequence_number
    FROM benchmark_digits d0
    CROSS JOIN benchmark_digits d1
    CROSS JOIN benchmark_digits d2
    CROSS JOIN benchmark_digits d3
    CROSS JOIN benchmark_digits d4
) sequence_values
WHERE sequence_number < 50000;

INSERT INTO benchmark_san_pham_bien_the (id, product_id, sku, deleted_at)
SELECT
    UNHEX(MD5(CONCAT('variant-', sequence_number, '-', variant_number))),
    UNHEX(MD5(CONCAT('product-', sequence_number))),
    CONCAT('SKU-', LPAD(sequence_number, 5, '0'), '-', LPAD(variant_number, 2, '0')),
    NULL
FROM (
    SELECT d0.number
         + d1.number * 10
         + d2.number * 100
         + d3.number * 1000
         + d4.number * 10000 AS sequence_number
    FROM benchmark_digits d0
    CROSS JOIN benchmark_digits d1
    CROSS JOIN benchmark_digits d2
    CROSS JOIN benchmark_digits d3
    CROSS JOIN benchmark_digits d4
) sequence_values
CROSS JOIN (SELECT 1 AS variant_number UNION ALL SELECT 2) variants
WHERE sequence_number < 50000;

ANALYZE TABLE benchmark_san_pham_san_pham, benchmark_san_pham_bien_the;

SELECT COUNT(*) AS product_count FROM benchmark_san_pham_san_pham;
SELECT COUNT(*) AS variant_count FROM benchmark_san_pham_bien_the;

EXPLAIN ANALYZE
SELECT p.id
FROM benchmark_san_pham_san_pham p
WHERE p.deleted_at IS NULL
  AND MATCH(p.name, p.slug) AGAINST ('+wireless* +mechanical*' IN BOOLEAN MODE)
ORDER BY p.created_at DESC, p.id ASC
LIMIT 20;

EXPLAIN ANALYZE
SELECT v.product_id
FROM benchmark_san_pham_bien_the v
WHERE v.deleted_at IS NULL
  AND v.sku LIKE 'SKU-04200%' ESCAPE '!';

EXPLAIN ANALYZE
SELECT p.id
FROM benchmark_san_pham_san_pham p
WHERE p.category_id = UNHEX(MD5('category-0'))
  AND p.status = 'PUBLISHED'
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id ASC
LIMIT 20;

EXPLAIN ANALYZE
SELECT candidate.id
FROM (
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_san_pham p
    WHERE p.deleted_at IS NULL
      AND MATCH(p.name, p.slug) AGAINST ('+wireless* +mechanical*' IN BOOLEAN MODE)
    UNION
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_san_pham p
    WHERE p.deleted_at IS NULL
      AND p.name LIKE 'wireless mechanical%' ESCAPE '!'
    UNION
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_san_pham p
    WHERE p.deleted_at IS NULL
      AND p.slug LIKE 'wireless mechanical%' ESCAPE '!'
    UNION
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_bien_the v
    JOIN benchmark_san_pham_san_pham p ON p.id = v.product_id
    WHERE p.deleted_at IS NULL
      AND v.deleted_at IS NULL
      AND v.sku LIKE 'wireless mechanical%' ESCAPE '!'
) candidate
ORDER BY candidate.created_at DESC, candidate.id ASC
LIMIT 20;

EXPLAIN ANALYZE
SELECT COUNT(*)
FROM (
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_san_pham p
    WHERE p.deleted_at IS NULL
      AND MATCH(p.name, p.slug) AGAINST ('+wireless* +mechanical*' IN BOOLEAN MODE)
    UNION
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_san_pham p
    WHERE p.deleted_at IS NULL
      AND p.name LIKE 'wireless mechanical%' ESCAPE '!'
    UNION
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_san_pham p
    WHERE p.deleted_at IS NULL
      AND p.slug LIKE 'wireless mechanical%' ESCAPE '!'
    UNION
    SELECT p.id, p.name, p.created_at, p.updated_at
    FROM benchmark_san_pham_bien_the v
    JOIN benchmark_san_pham_san_pham p ON p.id = v.product_id
    WHERE p.deleted_at IS NULL
      AND v.deleted_at IS NULL
      AND v.sku LIKE 'wireless mechanical%' ESCAPE '!'
) candidate;

DROP TABLE benchmark_san_pham_bien_the;
DROP TABLE benchmark_san_pham_san_pham;
DROP TABLE benchmark_digits;
