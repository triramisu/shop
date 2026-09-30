# API Catalog công khai và quản trị

## Phạm vi M2.2–M2.6

M2.2 cung cấp API quản trị category, product và variant trên nền domain M2.1. M2.5 hoàn thiện tìm kiếm theo tên, slug và SKU bằng query/index đã benchmark. M2.6 tách rõ API cửa hàng chỉ đọc khỏi API quản trị và thay phân quyền tạm theo vai trò bằng permission Catalog chi tiết. API dùng request/response DTO riêng và không trả JPA entity ra ngoài module.

Hai base path có trách nhiệm khác nhau:

- `/api/catalog`: API công khai, chỉ cho phép `GET`, không yêu cầu access token và chỉ trả dữ liệu được phép bán.
- `/api/admin/catalog`: API quản trị, yêu cầu JWT và permission phù hợp trên từng endpoint.

## Endpoint công khai

| Method | Path | Mục đích |
| --- | --- | --- |
| `GET` | `/api/catalog/categories` | Danh mục đang hoạt động |
| `GET` | `/api/catalog/products` | Tìm kiếm và phân trang product `PUBLISHED` |
| `GET` | `/api/catalog/products/{productId}` | Chi tiết product `PUBLISHED` thuộc danh mục hoạt động |
| `GET` | `/api/catalog/products/{productId}/images` | Ảnh của product hợp lệ ở cửa hàng |

Public request không có trường `status`; nếu client cố gửi query parameter này thì server vẫn cưỡng chế `PUBLISHED`. Response công khai dùng DTO riêng, chỉ trả biến thể đang bán và không lộ trạng thái nội bộ, version optimistic locking, object key, tên file gốc hoặc audit timestamp.

## Endpoint quản trị và permission

| Permission | Endpoint |
| --- | --- |
| `CATALOG_READ` | Các `GET /api/admin/catalog/**` |
| `CATALOG_WRITE` | Tạo/sửa category, product, variant và đổi trạng thái category/variant |
| `CATALOG_PUBLISH` | Công bố hoặc ẩn product |
| `CATALOG_IMAGE_MANAGE` | Tải lên, sắp xếp hoặc xóa ảnh product |

Migration V12 gán bốn permission cho `ADMIN` và `STAFF`. Việc kiểm tra dựa trên authority trong JWT, không hard-code tên role, nên vai trò tùy chỉnh có thể chỉ nhận đúng quyền cần thiết. V12 thu hồi các refresh session đang hoạt động để người dùng phải đăng nhập lại và nhận JWT chứa bộ permission mới.

Hệ thống hiện chưa có shop/merchant owner trong domain, do đó chưa áp dụng kiểm tra quyền sở hữu sản phẩm. Quy tắc ownership sẽ được bổ sung cùng phân hệ người bán; không suy diễn ownership từ người tạo bản ghi.

Các mutation variant trả lại toàn bộ `ProductResponse`, nhờ đó client nhận ngay product version mới và toàn bộ variant version để thực hiện thao tác kế tiếp.

## Concurrency và invariant

- Client bắt buộc gửi `version` nhận từ response trước. Version cũ trả HTTP `409` với `CATALOG_CONFLICT`.
- Unique constraint trong database vẫn là lớp bảo vệ cuối cho category code/slug, product slug và SKU; service chuyển lỗi race condition thành error code nghiệp vụ ổn định.
- Không thể publish product khi category không hoạt động hoặc chưa có variant hoạt động.
- Không thể tắt variant hoạt động cuối cùng của product đang publish.
- Không thể tắt category đang còn product chưa xóa.
- Giá dùng `BigDecimal`, không âm, tối đa hai chữ số thập phân; currency phải là mã ISO 4217 dùng được để định giá.

## Phân trang và sort

`GET /api/admin/catalog/products` nhận `page`, `size`, `keyword`, `categoryId`, `status`, `sortBy` và `direction`. `GET /api/catalog/products` nhận cùng tham số trừ `status`.

- `size` nằm trong `1..100`.
- `sortBy` chỉ nhận `CREATED_AT`, `UPDATED_AT`, `NAME`.
- `direction` chỉ nhận `ASC`, `DESC`.
- Mọi sort đều thêm `id ASC` làm tie-breaker để kết quả ổn định giữa các trang.
- Ký tự `!`, `%`, `_` trong keyword được escape trước khi tạo biểu thức `LIKE`; client không thể biến input thành wildcard ngoài ý muốn.
- MySQL dùng FULLTEXT cho tên/slug và B-tree prefix cho tên/slug/SKU; H2 trong test dùng fallback `LIKE` tương thích.
- Chi tiết query plan, index và cách benchmark nằm trong [catalog-search.md](catalog-search.md).
