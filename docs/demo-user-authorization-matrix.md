# DEMO-01B.1 — Ma trận USER/token/ownership tại local

## Hợp đồng tám phần

1. Mục tiêu: chứng minh contract anonymous/USER/admin-denied trên cùng bộ API,
   token sai/refresh token/revoked token không dùng làm access token và hai USER
   không đọc/sửa dữ liệu nhau. Không gọi production hoặc VNPay, tạo ADMIN, sửa
   role bằng SQL, dùng password chung trên web public hoặc thay security policy.
2. Identity sở hữu đăng ký/token; Catalog public; Order sở hữu cart/order/payment
   query. Dùng API thật qua MockMvc và fixture test thuộc Order; không thêm phụ
   thuộc production xuyên module, không sửa schema/Flyway V1–V25.
3. Ma trận public 200, private anonymous 401, USER own API 200, USER admin 403.
   Order/payment khác chủ và UUID không tồn tại phải cùng 404/1310; cart item
   khác chủ/missing cùng 404/1301. Không đổi response chỉ để làm test xanh.
4. Không dùng JWT/secret/account thật. Register/login tạo USER mặc định qua API;
   fixture dùng password test sẵn có, chỉ trong test profile. Không publish
   credential, token hoặc presigned URL; không test refund/payment thật.
5. Đầu ra: integration-test ma trận liên module và biên bản/README; không có
   thay đổi controller/service, dependency, profile dev/prod hoặc migration.
6. Kiểm cả response và side effect: foreign mutation không đổi cart của chủ;
   history chỉ chứa đơn principal, request ownerSubject không đổi ownership;
   logout revoke làm access token cũ mất hiệu lực, kể cả khi gửi vào API public.
7. Nghiệm thu local: focused tests, regression Identity/Order/Catalog/Modulith,
   Spotless và diff check. Chạy full verify khi Docker sẵn; chỉ MySQL test mới
   được dùng để kết luận locking/concurrency. MockMvc/H2 không là bằng chứng live.
8. Không AWS mutation hoặc deploy. Rollback test/docs không đổi dữ liệu runtime.
   Giữ lab nghỉ; live USER/SIT và merge/CI là gate riêng, không tự đánh dấu task
   cha DEMO-01 hay Payment hoàn tất từ gate local.

## Bộ API được kiểm tra

| GET endpoint | Anonymous | USER |
|---|---:|---:|
| /api/catalog/categories | 200 | 200 |
| /api/catalog/products | 200 | 200 |
| /api/auth/my-info | 401 | 200 |
| /api/cart | 401 | 200 |
| /api/orders | 401 | 200 |
| /api/admin/catalog/products | 401 | 403 |
| /api/admin/inventory/stock-items | 401 | 403 |
| /api/system-administration/users | 401 | 403 |

PermitAll không có nghĩa bỏ validation: nếu client chủ động gửi Bearer token sai,
refresh token hoặc access token đã revoke, JWT filter phải từ chối. Webhook IPN
là ngoại lệ không dùng JWT nhưng vẫn cần chữ ký; không đưa callback vào bảng
USER 200/403 hoặc gọi nó để giả tạo thành công payment.

## Chạy lại

PowerShell, gốc repository; đặt JAVA_HOME theo JDK 21 thực tế. Không đổi global
environment và không lấy credential từ production.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12.1'
.\mvnw.cmd --batch-mode '-Dtest=DemoUserAuthorizationMatrixTests' test
.\mvnw.cmd --batch-mode clean verify
git diff --check
```

Focused test dùng profile test/H2, test transaction rollback; full verify còn
chạy MySQL/MinIO Testcontainers. Không chạy lệnh đó trên EC2 production.

## Kết quả và giới hạn

Ngày 23/10/2026, focused test đầu tiên đạt **19 test**, 0 failure/error/skipped.
Lượt full verify đầu tiên chạy 556 test và bị chặn bởi một architecture test:
class test đặt ở `com.shop.order` không được gọi repository. Đã chuyển test
về `com.shop.order.internal.service`, giữ nguyên ArchUnit và production code.
Lượt cuối `spotless:apply clean verify` kết thúc lúc 08:39:20 UTC+7, đạt
**556 test**, 0 failure/error/skipped, gồm cả 19 ca ma trận trên bản cuối,
MySQL 8.0.46/MinIO Testcontainers và kiểm tra ranh giới module. Spotless,
JaCoCo gate và `git diff --check` đạt. Line coverage 91,17%, branch 70,27%;
vẫn chưa đạt mục tiêu toàn dự án >80% branch. Baseline của nhánh này là
`c54c1b3`, chưa chứa 133 test mới của hai PR VNPay; không cộng hai lần hay
coi 556 là tổng sau tích hợp. Không lấy 555 ca qua trong lượt lỗi đầu làm
bằng chứng full gate đã đạt.
AWS vẫn ở trạng thái nghỉ đã kiểm kê; chưa có live USER200/admin403/IDOR evidence,
chưa đăng ký hai USER trên web, chưa merge/push/deploy hoặc bật payment.
