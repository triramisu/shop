# Thiết kế tìm kiếm Catalog

## Phạm vi và contract

`GET /api/catalog/products` giữ nguyên request/response đã công bố. `keyword` tìm theo tên/slug sản phẩm và SKU; `categoryId`, `status`, `page`, `size`, `sortBy`, `direction` là các filter/phân trang độc lập. Sort chỉ nhận `CREATED_AT`, `UPDATED_AT`, `NAME` và luôn thêm `id ASC` làm tie-breaker.

Keyword được chuẩn hóa Unicode NFKC, trim, gộp khoảng trắng và lowercase. `%`, `_`, `!` luôn được escape trước biểu thức `LIKE`; toán tử Boolean Full-Text không được lấy trực tiếp từ client. Các token chữ/số dài từ ba ký tự được chuyển thành dạng bắt buộc và prefix, ví dụ `Điện thoại` thành `+điện* +thoại*`.

## Phương án thực thi

Production/dev dùng strategy `mysql-fulltext`:

1. Nhánh `MATCH(name, slug) AGAINST (... IN BOOLEAN MODE)` dùng FULLTEXT index cho từ nằm ở bất kỳ vị trí nào.
2. Hai nhánh B-tree prefix cho `name` và `slug` xử lý từ ngắn hoặc cụm bắt đầu tên.
3. Nhánh B-tree prefix trên unique SKU tìm đúng biến thể bán.
4. Các nhánh dùng `UNION` để loại product trùng, sau đó mới sort và phân trang.
5. Query lấy ID trước; aggregate được hydrate qua JPA/EntityGraph và sắp lại theo thứ tự ID trả về. Không trả native row hoặc entity thiếu quan hệ ra API.

Test H2 dùng strategy `like` vì H2 không hỗ trợ cú pháp FULLTEXT của MySQL. MySQL Testcontainers luôn ép strategy production để tránh tình trạng chỉ kiểm thử fallback.

Stored procedure chưa được sử dụng: query hiện tại có parameter binding, allowlist sort, query plan quan sát được và không cần logic đặt trong database. Chỉ xem xét lại khi benchmark production-like chứng minh query/index không đạt SLO.

## Index Flyway V11

- `ft_san_pham_san_pham_tim_kiem(name, slug)` cho full-text.
- Ba index `deleted_at + sort column + id` cho danh sách active theo từng sort.
- `category_id + status + deleted_at + created_at + id` cho filter phổ biến.
- Unique index SKU từ V9 tiếp tục phục vụ SKU prefix; không tạo index trùng.

MySQL có thể rebuild table khi thêm FULLTEXT index. Cần chạy migration trong maintenance window nếu bảng production đã lớn và theo dõi metadata lock. Rollback an toàn ở tầng ứng dụng là đặt `CATALOG_SEARCH_STRATEGY=like`; các index V11 có thể giữ lại. Nếu buộc phải xóa index, thực hiện bằng migration roll-forward riêng sau khi xác nhận không có phiên bản ứng dụng nào còn dùng `MATCH`.

## Ngưỡng nghiệm thu query plan

- Dưới 10.000 product, cost-based optimizer được phép chọn table scan nếu rẻ hơn.
- Từ 10.000 product trở lên, nhánh full-text phải dùng access `fulltext`, nhánh SKU phải dùng `range/ref`, và filter category/status phải dùng composite index; `ALL` ở các nhánh này là lỗi nghiệm thu.
- `size` tối đa 100; benchmark chuẩn dùng 50.000 product, 100.000 variant và page size 20.
- SLO tạm thời cho máy local sau warm-up: mỗi kịch bản `EXPLAIN ANALYZE` hoàn tất dưới 100 ms. SLO phải được đo lại trên staging có cấu hình gần production trước khi phát hành.

## Kết quả benchmark local

Kết quả ngày 30/09/2026 trên MySQL 8.0.46 chạy bằng Docker Desktop, với 50.000 product và 100.000 variant:

| Kịch bản | Access path chính | Thời gian thực tế |
| --- | --- | ---: |
| Full-text `wireless mechanical` | FULLTEXT index | 8,55 ms |
| SKU prefix `SKU-04200%` | B-tree range | 0,04 ms |
| Category + status | Composite index lookup | 0,49 ms |
| Hợp nhất các nhánh và lấy page 20 | Các bảng gốc đều dùng index | 27,3 ms |
| Đếm candidate để trả `totalElements` | Các bảng gốc đều dùng index | 23,0 ms |

`UNION` materialize 5.000 candidate rồi scan derived table để deduplicate/sort; đây không phải full scan bảng product/variant. Tiêu chí `không ALL` áp dụng cho các nhánh đọc bảng gốc. Warm run page + count là khoảng 50,3 ms, dưới SLO local 100 ms. SLO HTTP vẫn phải được đo lại trên staging vì con số này chưa gồm network, mapping và serialize response.

Chạy benchmark bằng MySQL local/disposable:

```powershell
Get-Content scripts/benchmark/catalog-search.sql -Raw |
  docker exec -i mysql-8.0 mysql -ushop -pshop-local-password shop
```

Script chỉ tạo các bảng `benchmark_*` độc lập, nạp dữ liệu, in năm execution plan rồi xóa toàn bộ bảng vừa tạo. Mỗi lần chạy đều cleanup chính xác các bảng này trước khi bắt đầu nên có thể chạy lại sau một lần dừng lỗi. Không chạy trên production.

## Giới hạn đã biết

InnoDB FULLTEXT có stopword và giới hạn token. Prefix B-tree hỗ trợ từ ngắn ở đầu tên/SKU nhưng không thay thế search engine chuyên dụng cho typo, ranking, synonym hoặc phân tích tiếng Việt sâu. Khi catalog tăng tới mức cần các tính năng đó, phải benchmark OpenSearch/Elasticsearch như read model riêng; database Catalog vẫn là nguồn dữ liệu chuẩn.
