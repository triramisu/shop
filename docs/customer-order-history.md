# WEB-01 — Lịch sử và chi tiết đơn của khách

[Mục lục tài liệu](README.md) · [Triển khai](../deploy/README.md)

## 1. Mục tiêu, actor, phạm vi

Khách đã đăng nhập đọc lại danh sách/chi tiết đơn của chính mình sau reload hoặc đăng nhập lại. Chỉ làm API đọc và UI tương ứng; không thêm quyền xem đơn người khác cho ADMIN, hủy đơn, refund, retry payment, shipping hay sửa giỏ sau checkout.

## 2. Dữ liệu và phụ thuộc

Order sở hữu entity/repository/query. Tái sử dụng `OrderSnapshotQueryService`, immutable item snapshot và mapper; không gọi repository nội bộ của module khác. Schema V24 và index owner/created từ V16 đã đủ, không thêm/sửa migration.

## 3. API và bất biến

- `GET /api/orders?page=0&size=10`: page 0–10000, size 1–50; luôn `createdAt DESC, id DESC`. Tham số owner/sort lạ không được bind hoặc ảnh hưởng truy vấn.
- Response summary có id, status, createdAt, currency, grandTotal, itemCount (số dòng), totalQuantity (số đơn vị), firstProductName; page có content/page/size/totalElements/totalPages/first/last/sort.
- `GET /api/orders/{orderId}`: dùng lại DTO snapshot checkout, trả trạng thái hiện tại ở Order. Không đưa owner, provider credential/action URL hoặc entity JPA vào response.
- Owner chỉ lấy từ Principal. Anonymous 401; UUID/query sai 400; đơn không tồn tại hoặc khác owner đều 404 với `ORDER_NOT_FOUND=1310` và cùng message. ADMIN/STAFF cũng chỉ được đọc đơn của mình qua API khách.
- Header response thành công `Cache-Control: private, no-store`; request chỉ đọc, không thay stock/payment/order hoặc gọi outbound provider.

## 4. Bảo mật, đồng thời và hiệu năng

Owner nằm trong SQL của page ID/count, batch snapshot và detail. Không fetch collection kèm LIMIT/OFFSET; page ID trước, batch theo owner sau, sắp lại đúng thứ tự ID từ database. Page rỗng không chạy query batch. Transaction read-only giữ mapping trong persistence context. Không log payload đơn.

UI query key có customer ID, chờ hồ sơ xác thực sẵn sàng; cancel/remove cache đơn khi đổi/xóa phiên, không cho response hồ sơ cũ ghi đè user mới. Detail chỉ dùng dữ liệu server; checkout chuyển theo order ID thay vì giữ snapshot nhạy cảm trên history. Route xác nhận cũ chỉ bridge tới GET detail, không render snapshot của tài khoản trước.

HTTP client có revision cho login/logout và rotate có điều kiện theo refresh token: response riêng tư của phiên cũ bị hủy; refresh đến muộn không ghi đè hoặc xóa phiên mới. Rotate bình thường không đổi revision và giữ single-flight. Thay đổi này có test riêng, không hạ rate limit/CAPTCHA hay thay cơ chế JWT backend.

## 5. Đầu ra

Order query DTO/controller, paging repository/service/summary mapper, unit + security/API + H2/MySQL query test và OpenAPI assertions. UI thêm `/orders`, `/orders/:orderId`, trạng thái loading/rỗng/lỗi/404, phân trang, tải lại trạng thái, liên kết navigation/profile/checkout. Không bịa trạng thái thanh toán hoặc tiến độ vận chuyển.

## 6. Nghiệm thu

Đúng owner, cross-owner kể cả ADMIN, missing, anonymous, invalid UUID/query, page/size biên, trang rỗng/quá cuối, timestamp trùng, batch nhiều dòng không nhân bản root, không N+1 hoặc phân trang collection trong RAM, tổng tiền/số lượng lấy snapshot, GET không mutation; logout/login tài khoản khác không hiển thị cache hoặc confirmation cũ. Kiểm tra MySQL thật trong Testcontainers, không kết luận từ H2 riêng.

## 7. Kiểm chứng

Maven test đúng phạm vi rồi regression phù hợp + Modulith/ArchUnit/OpenAPI, Spotless, JaCoCo, `git diff --check`; UI TypeScript/Vite build và browser smoke desktop/mobile trên QA riêng. Khi đóng gói dừng đúng backend local giữ JAR rồi khởi động lại, tránh lỗi Windows file lock. Ghi rõ test thật/skip/coverage và rủi ro còn lại; không suy diễn CI xanh.

## 8. Rollout, tương thích, rollback

API additive, không đổi POST checkout/state machine/schema. Deploy backend trước UI; rollback artifact UI/backend riêng, giữ dữ liệu đơn. Repo thực tế là `D:/shop` và `D:/shop-ui`, giữ toàn bộ thay đổi layout/UI có sẵn. Chưa commit/push/merge/deploy nếu không có ủy quyền tương ứng. Đây là đúng một feature; chưa tự mở Payment task.

## Bằng chứng local — 16/10/2026

- Targeted suite: **31 test đạt**, gồm unit/service, HTTP security với JWT thật, H2, MySQL 8.0.46 Testcontainers, OpenAPI và Modulith/ArchUnit. ADMIN bị 404 khi đọc đơn người khác; missing/foreign có cùng body; anonymous 401; invalid UUID/page/size 400.
- Page ID/count/batch có ownership, timestamp trùng dùng ID DESC; test nhiều item không nhân bản đơn, query budget tối đa 3 và bật fail-on-pagination-over-collection-fetch. Không thêm/sửa migration V1–V24 hoặc entity/state machine.
- Full Maven `verify`: **418 test, 0 failure/error/skipped**, **BUILD SUCCESS**, Spotless và JaCoCo đạt gate hiện có. Coverage instruction **90,78%**, line **90,62%**, branch **68,01%**; chưa đạt mục tiêu cuối dự án branch trên 80%. Không đổi threshold/exclusion.
- UI: **6 test Node** dùng TypeScript hiện có, không thêm dependency. Kiểm tra late refresh sau logout, refresh lỗi sau đổi tài khoản, response cũ bị hủy, single-flight hợp lệ, AbortSignal và hủy/xóa cache riêng tư nhưng giữ catalog public. `npm run build` chạy TypeScript + 6 test rồi Vite build.
- Browser QA tại 5174 → backend 8081 dùng schema `shop_ui_qa_20261015`, bucket `shop-ui-review-20261015`, provider fake. Khách xem đơn có sẵn, reload detail vẫn đọc đúng snapshot; đổi sang ADMIN không xem được cùng mã đơn, lịch sử ADMIN rỗng, đăng nhập lại khách quay về đúng URL.
- Checkout QA mới tạo đơn `3241e3da-eca7-44bf-8d9d-1b982b81519b`, 2 dòng/3 sản phẩm, tổng **5.570.000 VND**; mở trực tiếp `/orders/{id}` và reload thành công. Đây là đơn giả lập trong schema QA, không phải giao dịch VNPay/Stripe thật. Giỏ vẫn giữ đúng contract cũ, không xóa tự động.
- Kiểm tra trang quá cuối `page=10000` và nút về đầu; lịch sử hiển thị 2 đơn QA mới nhất trước. Viewport mobile 390 và desktop 1440 không tràn ngang (client width thực tế trừ scrollbar lần lượt 375/1425).
- Docker Desktop ban đầu chưa chạy; đã khởi động lại để test MySQL thật, không bỏ qua test. Hai lỗi fixture ban đầu (role_code và namespace test theo ArchUnit) đã sửa theo schema/rule thật, không nới test kiến trúc. Log targeted r2 và full regression được giữ ngoài repo.

Log/screenshot ở artifact local:
`C:/Users/minhp/.codex/visualizations/2026/10/06/01a10eda-4440-7702-9f71-ba84dd0e31c5/`:
`web01-targeted-r2.log`, `web01-regression.log`, `web01-order-desktop.jpg`,
`web01-order-mobile.jpg`, `web01-orders-preview.jpg`.

## Giới hạn và điểm dừng

Chưa commit/push/merge, chưa có CI hoặc deploy; chưa chạy Sonar (không đóng milestone), chưa kiểm thử payment sandbox thật. JWT vẫn dùng cơ chế localStorage hiện có; đồng bộ phiên nhiều tab và chuyển sang cookie HttpOnly/BFF không nằm trong task này. Browser smoke không thay thế bộ E2E tự động gần production. WEB-01 đạt phạm vi local đã kiểm tra; nhiệm vụ đề xuất tiếp theo là M5.5B theo roadmap, không tự bắt đầu.
