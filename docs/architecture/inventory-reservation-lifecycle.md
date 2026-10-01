# Vòng đời giữ tồn kho

## State machine

Mỗi bản ghi `ton_kho_giu_hang` bắt đầu ở `RESERVED` và chỉ được chuyển một lần sang một trạng thái kết thúc:

| Chuyển trạng thái | Điều kiện | Thay đổi `on_hand` | Thay đổi `reserved_quantity` |
| --- | --- | ---: | ---: |
| `RESERVED -> CONFIRMED` | Chưa đến `expires_at` | `-quantity` | `-quantity` |
| `RESERVED -> RELEASED` | Nhận lệnh hủy khi vẫn còn `RESERVED` | `0` | `-quantity` |
| `RESERVED -> EXPIRED` | `expires_at <= cutoff` | `0` | `-quantity` |

Không có chuyển trạng thái ra khỏi `CONFIRMED`, `RELEASED` hoặc `EXPIRED`. Yêu cầu lặp lại hiện trả conflict; idempotency theo request sẽ được xử lý riêng ở M3.4.

Nếu lệnh confirm đến đúng hoặc sau thời điểm hết hạn, transaction sẽ chuyển reservation sang `EXPIRED`, hoàn lại số lượng đang giữ rồi commit. Sau đó API nội bộ trả lỗi nghiệp vụ `STOCK_RESERVATION_EXPIRED`. Việc tách hai bước này bảo đảm lỗi trả về không rollback thao tác hết hạn vừa được xác định.

## Thứ tự khóa và transaction

Mỗi confirm, release hoặc expire chạy trong transaction `REQUIRES_NEW` với timeout cấu hình. Thứ tự khóa luôn cố định:

1. Khóa bi quan bản ghi `ton_kho_giu_hang` theo `reservation_id`.
2. Kiểm tra reservation còn ở `RESERVED` và, với expire, đã đến hạn.
3. Khóa bi quan bản ghi `ton_kho_mat_hang` tương ứng.
4. Cập nhật số dư, trạng thái và append một bản ghi `ton_kho_bien_dong`.
5. Phát `StockReservationStatusChangedEvent` và `StockBalanceChangedEvent` trong cùng transaction.

Thứ tự này ngăn confirm, release và expiration cùng thay đổi một reservation. Toàn bộ thay đổi rollback nếu bất kỳ bước nào thất bại. Event hiện là event nội bộ cùng tiến trình và không phải cam kết giao nhận bền vững; khi tách microservice cần chuyển sang transactional outbox.

## Expiration job

Job chọn tối đa `batch-size` ID đang `RESERVED` có `expires_at <= cutoff`, theo thứ tự hết hạn rồi ID. Mỗi ID được xử lý trong một transaction độc lập, vì vậy:

- lỗi một reservation không rollback các reservation đã hoàn tất;
- tiến trình chết giữa batch không làm mất các mục chưa xử lý, vì lần chạy sau vẫn chọn lại chúng;
- nhiều instance có thể chọn cùng một ID, nhưng khóa hàng và kiểm tra lại trạng thái bảo đảm chỉ một instance thay đổi số dư;
- batch chạy lại sau khi hoàn thành không tạo thêm movement.

Cấu hình runtime:

- `INVENTORY_EXPIRATION_ENABLED`, mặc định `true`;
- `INVENTORY_EXPIRATION_FIXED_DELAY`, mặc định `30s`;
- `INVENTORY_EXPIRATION_INITIAL_DELAY`, mặc định `30s`;
- `INVENTORY_EXPIRATION_BATCH_SIZE`, mặc định `100`, giới hạn `1..1000`.

Các metric có cardinality hữu hạn:

- `shop.inventory.reservation.expiration.runs{outcome=success|failed}`;
- `shop.inventory.reservation.expiration.items{outcome=expired|skipped|failed}`;
- `shop.inventory.reservation.operations{operation=confirm|release|expire,outcome=...}`.

## Triển khai và quay lui

Schema V13 đã có đủ status, movement type và index `(status, expires_at)`, nên M3.3 không thêm migration. Khi triển khai, có thể đặt `INVENTORY_EXPIRATION_ENABLED=false` để tạm dừng job mà không dừng confirm/release. Quay lui ứng dụng không làm mất dữ liệu; các reservation đã ở trạng thái kết thúc vẫn hợp lệ với schema V13.
