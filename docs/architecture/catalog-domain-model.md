# Mô hình domain Catalog

## Phạm vi M2.1

M2.1 chỉ tạo domain model, persistence mapping, repository contract và Flyway migration. Controller, request/response DTO, service CRUD, phân trang, tìm kiếm và ảnh thuộc các nhiệm vụ M2.2–M2.6.

## Aggregate ownership

- `Category` là aggregate root riêng. Product chỉ tham chiếu một category đang `ACTIVE` và chưa bị xóa.
- `Product` là aggregate root sở hữu toàn bộ `ProductVariant` của nó. Variant chỉ được tạo thông qua `Product.addVariant`.
- `ProductVariant` là đơn vị bán thực tế. Inventory sau này chỉ giữ SKU/ID qua contract công khai, không phụ thuộc entity hoặc repository Catalog.
- Catalog không tạo foreign key sang Identity, Inventory, Order hoặc Payment.

## Trạng thái

```text
Category: ACTIVE ↔ INACTIVE → soft-deleted

Product: DRAFT → PUBLISHED → HIDDEN → PUBLISHED
             ↘      ↘          ↘
                    ARCHIVED (soft-delete, trạng thái kết thúc)

Variant: ACTIVE ↔ INACTIVE → ARCHIVED (soft-delete, trạng thái kết thúc)
```

Product chỉ được publish khi category còn khả dụng và có ít nhất một variant `ACTIVE`. Product bị xóa mềm sẽ archive toàn bộ variant trong cùng aggregate.

## Quy tắc định danh và xóa

- Tên bảng có prefix tiếng Việt `san_pham_` để nhận biết module sở hữu trong database.
- Category `code` và slug, Product slug, Variant SKU là duy nhất.
- Code và SKU được chuẩn hóa uppercase; slug được chuẩn hóa lowercase trước khi lưu.
- SKU/code/slug vẫn được giữ sau soft-delete và không được tái sử dụng. Quy tắc này bảo vệ tham chiếu từ tồn kho, đơn hàng và lịch sử audit.
- Repository contract không cung cấp `delete`/`deleteById`; xóa nghiệp vụ phải gọi domain method soft-delete.
- Category chỉ được xóa/deactivate khi service đã kiểm tra không còn product chưa xóa bằng `existsByCategoryIdAndDeletedAtIsNull`.
- Foreign key dùng `ON DELETE RESTRICT` làm lớp bảo vệ cuối trước thao tác xóa vật lý ngoài ý muốn.

## Giá và tiền tệ

- Giá dùng `BigDecimal`, lưu `DECIMAL(19,2)` và không được âm.
- Không tự làm tròn giá có quá hai chữ số thập phân; request phải bị từ chối để tránh thay đổi giá âm thầm.
- Currency dùng mã ISO 4217 ba ký tự đã chuẩn hóa uppercase.
- Client không được cung cấp tổng tiền Order; Order sau này lấy giá hiện hành từ contract Catalog và lưu snapshot.

## Audit fields và concurrency

Mỗi bảng có UUID, `version`, `created_at`, `updated_at` và `deleted_at`. `@Version` bảo vệ optimistic locking cho M2.2; timestamp được lưu UTC theo cấu hình Hibernate hiện tại. Lịch sử thay đổi chi tiết không nhồi vào entity mà sẽ phát event sang module Audit theo catalog M6.9A.
