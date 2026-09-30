# API quản trị Catalog

## Phạm vi M2.2–M2.5

M2.2 cung cấp API quản trị category, product và variant trên nền domain M2.1. M2.5 hoàn thiện tìm kiếm theo tên, slug và SKU bằng query/index đã benchmark. API dùng request/response DTO riêng, MapStruct mapper và không trả JPA entity ra ngoài module.

Base path là `/api/catalog`. Trong giai đoạn này toàn bộ endpoint yêu cầu access token có `ROLE_ADMIN` để mặc định đóng quyền. M2.6 sẽ thay điều kiện tạm này bằng permission Catalog chi tiết và bổ sung API đọc công khai chỉ trả product đã `PUBLISHED`.

## Endpoint

| Method | Path | Mục đích |
| --- | --- | --- |
| `GET` | `/categories` | Danh sách category chưa xóa |
| `POST` | `/categories` | Tạo category |
| `PUT` | `/categories/{categoryId}` | Sửa category theo `version` |
| `PATCH` | `/categories/{categoryId}/status` | Bật/tắt category theo `version` |
| `GET` | `/products` | Tìm kiếm, lọc, sort và phân trang |
| `GET` | `/products/{productId}` | Chi tiết aggregate product |
| `POST` | `/products` | Tạo product ở trạng thái `DRAFT` |
| `PUT` | `/products/{productId}` | Sửa product theo `version` |
| `PATCH` | `/products/{productId}/publish` | Publish product hợp lệ |
| `PATCH` | `/products/{productId}/hide` | Ẩn product đã publish |
| `POST` | `/products/{productId}/variants` | Thêm variant theo product `version` |
| `PUT` | `/products/{productId}/variants/{variantId}` | Sửa variant theo variant `version` |
| `PATCH` | `/products/{productId}/variants/{variantId}/status` | Bật/tắt variant theo variant `version` |

Các mutation variant trả lại toàn bộ `ProductResponse`, nhờ đó client nhận ngay product version mới và toàn bộ variant version để thực hiện thao tác kế tiếp.

## Concurrency và invariant

- Client bắt buộc gửi `version` nhận từ response trước. Version cũ trả HTTP `409` với `CATALOG_CONFLICT`.
- Unique constraint trong database vẫn là lớp bảo vệ cuối cho category code/slug, product slug và SKU; service chuyển lỗi race condition thành error code nghiệp vụ ổn định.
- Không thể publish product khi category không hoạt động hoặc chưa có variant hoạt động.
- Không thể tắt variant hoạt động cuối cùng của product đang publish.
- Không thể tắt category đang còn product chưa xóa.
- Giá dùng `BigDecimal`, không âm, tối đa hai chữ số thập phân; currency phải là mã ISO 4217 dùng được để định giá.

## Phân trang và sort

`GET /products` nhận `page`, `size`, `keyword`, `categoryId`, `status`, `sortBy` và `direction`.

- `size` nằm trong `1..100`.
- `sortBy` chỉ nhận `CREATED_AT`, `UPDATED_AT`, `NAME`.
- `direction` chỉ nhận `ASC`, `DESC`.
- Mọi sort đều thêm `id ASC` làm tie-breaker để kết quả ổn định giữa các trang.
- Ký tự `!`, `%`, `_` trong keyword được escape trước khi tạo biểu thức `LIKE`; client không thể biến input thành wildcard ngoài ý muốn.
- MySQL dùng FULLTEXT cho tên/slug và B-tree prefix cho tên/slug/SKU; H2 trong test dùng fallback `LIKE` tương thích.
- Chi tiết query plan, index và cách benchmark nằm trong [catalog-search.md](catalog-search.md).
