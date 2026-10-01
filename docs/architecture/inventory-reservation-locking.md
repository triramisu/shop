# Chiến lược giữ tồn kho nguyên tử

## Quyết định

Shop dùng atomic conditional update thay vì đọc số dư rồi khóa bi quan:

```sql
UPDATE ton_kho_mat_hang
SET reserved_quantity = reserved_quantity + :quantity,
    version = version + 1,
    updated_at = :updatedAt
WHERE id = :stockItemId
  AND on_hand - reserved_quantity >= :quantity;
```

Spring Data khai báo cùng phép cập nhật bằng JPQL để H2 kiểm tra nhanh và MySQL/InnoDB thực thi trên runtime. InnoDB lấy exclusive row lock khi cập nhật. Điều kiện số dư được đánh giá trong cùng statement nên hai transaction tranh lượng tồn cuối không thể cùng thành công.

## Transaction boundary

Mỗi attempt chạy trong một transaction `REQUIRES_NEW`:

1. Từ chối `reservationId` đã tồn tại.
2. Atomic update `reserved_quantity` và `version` nếu còn đủ available.
3. Insert `ton_kho_giu_hang` ở trạng thái `RESERVED`.
4. Append movement `RESERVATION` và phát `StockBalanceChangedEvent`.

Bất kỳ bước nào lỗi đều rollback cả số dư, reservation và movement. Duplicate `reservationId` hiện trả conflict; replay idempotent sẽ được bổ sung ở M3.4.

## Retry và metrics

Chỉ `PessimisticLockingFailureException`, gồm deadlock/lock-acquisition failure đã được Spring dịch, mới được retry. Thiếu hàng, validation, không tìm thấy stock item và duplicate reservation là kết quả nghiệp vụ nên không retry.

- `app.inventory.reservation.max-attempts`: tổng attempt, mặc định `3`, giới hạn `1..5`.
- `app.inventory.reservation.transaction-timeout`: timeout mỗi transaction, mặc định `3s`, giới hạn `1s..30s`.
- `app.inventory.reservation.retry-backoff`: khoảng nghỉ cố định giữa attempt, mặc định `25ms`.
- `app.inventory.reservation.max-duration`: TTL tối đa caller được yêu cầu, mặc định `30m`.
- `shop.inventory.reservation.operations{outcome=...}`: kết quả command.
- `shop.inventory.reservation.lock.retries`: số lần retry do lock.

Khi cạn retry, service trả lỗi tạm thời `1221`/HTTP 503 và không retry vô hạn. M3.3 sẽ bổ sung confirm/release/expiration; M3.5 sẽ mở rộng stress test nhiều thread sau khi toàn bộ state transition hoàn chỉnh.
