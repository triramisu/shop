# Mô hình miền Inventory

## Ranh giới module

Inventory sở hữu số dư, biến động và giữ hàng. Inventory không đọc trực tiếp entity, repository hoặc bảng của
Catalog. Module chỉ gọi `CatalogSkuLookup` thuộc named interface `catalog :: inventory` để xác minh SKU và lấy
`productVariantId` chuẩn.

Thiết kế này giữ modular monolith có cùng ranh giới với microservice tương lai: khi tách dịch vụ, hợp đồng Java có
thể được thay bằng REST/gRPC hoặc dữ liệu tham chiếu được đồng bộ bằng sự kiện mà không đổi mô hình Inventory.

## Số dư tồn kho

Mỗi `StockItem` đại diện một SKU tại một địa điểm và có ba giá trị:

- `onHand`: số lượng vật lý đang có.
- `reserved`: số lượng đang được giữ cho đơn hàng.
- `available = onHand - reserved`: giá trị dẫn xuất, không lưu thành cột để tránh ba số dư lệch nhau.

Bất biến luôn phải đúng: `onHand >= 0`, `reserved >= 0`, `reserved <= onHand`.

## Lịch sử biến động

`StockMovement` là sổ cái append-only. Mỗi bản ghi lưu delta và số dư ngay sau thao tác. Ứng dụng không cung cấp
phương thức cập nhật/xóa biến động; khóa ngoại `ON DELETE RESTRICT` cũng bảo vệ lịch sử. `referenceId` dành cho mã
phiếu nhập, đơn hàng hoặc khóa idempotency ở các nhiệm vụ sau.

## Giữ hàng

`StockReservation` có định danh do bên gọi cấp, số lượng dương, trạng thái và `expiresAt`. M3.1 mới thiết lập entity,
schema, repository và bất biến. API reserve chưa được công bố ở M3.1 vì reserve an toàn phải cập nhật số dư nguyên tử;
khóa/conditional update đó là phạm vi M3.2. Chuyển trạng thái confirm, release và expire thuộc M3.3.

## Sự kiện miền

Sau khi khởi tạo hoặc điều chỉnh thành công, Inventory phát `StockBalanceChangedEvent`. Hợp đồng chứa định danh,
SKU, địa điểm, ba số dư, loại biến động, mã tham chiếu và thời điểm. Consumer nên xử lý sau commit; outbox và
idempotency liên dịch vụ sẽ được bổ sung trước khi tách microservice.
