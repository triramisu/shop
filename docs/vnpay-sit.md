# PAY-SIT-01C — Nghiệm thu VNPay sandbox qua website

## Hợp đồng tám phần — 25/10/2026

1. **Mục tiêu/actor/phạm vi:** kiểm chứng khách USER mua sản phẩm DEMO qua
   website, nhận handoff VNPay sandbox và kết quả IPN thực từ provider. Chủ
   merchant tự đăng nhập/nhập dữ liệu sandbox khi cần. Không thanh toán thật,
   tự hoàn tiền, bật query/worker/recovery, load test hoặc sửa hạ tầng.
2. **Ownership/phụ thuộc:** Identity sở hữu phiên, Order sở hữu cart/order,
   Inventory sở hữu giữ/confirm/release kho; Payment sở hữu attempt và IPN
   inbox. Dùng API/public contract hiện hành, không SQL mutation xuyên module.
   Phải có đăng ký IPN được xác minh, runtime prod/vnpay và đúng UI đã deploy.
   Baseline deployed LT7 từ `570922c`; nhánh task xếp trên baseline vận hành
   chưa merge, không lấy `origin/main` làm bằng chứng đã chứa thay đổi đó.
3. **Luồng/bất biến:** Order/giá/currency do server tính. Payment chỉ thành
   công từ IPN hợp lệ; browser return không được tự đổi Order thành PAID.
   Replay cùng checkout key không tạo thêm order/giữ kho. Callback lặp không
   tạo thêm transition/inbox hoặc confirm/release movement.
4. **An toàn/vận hành:** chỉ merchant sandbox và sản phẩm DEMO, quantity1;
   giữ credential ở secret/session trong RAM, không ghi password/token/action
   URL/QR/checksum/query thô vào README, ảnh, terminal hoặc analytics. Không
   lấy lại ADMIN bootstrap từ secret cũ. Dùng USER đăng nhập riêng; không tạo
   public account với mật khẩu chung. Không đổi WAF/SG/CAPTCHA để test qua.
   Dừng tạo order mới nếu callback không về hoặc không xác minh được IPN.
5. **Đầu ra:** ma trận kịch bản, report kết quả có thời điểm và nguồn bằng
   chứng đã redaction; hướng dẫn merchant/IPN, kiểm trạng thái và xử lý pending.
   Chỉ sửa code/image/schema nếu thực sự phát hiện lỗi, lập phạm vi/test/plan
   riêng trước deploy. Không tạo thêm callback giả thành công để lấp bằng chứng.
6. **Ca kiểm tra:** success, provider-declared failure/cancel, callback lặp,
   return giả thành công, callback đến muộn/không về. Chỉ test duplicate bằng
   chức năng retry/SIT provider nếu có; không tự ký IPN thành công bằng merchant
   key rồi gọi đó là provider E2E. Cần phân biệt ca local với ca live.
7. **Nghiệm thu:** email/merchant xác nhận đúng IPN; app mode/health/current
   flags đạt; một order kiểm soát nhận IPN thực, Order PAID, Payment SUCCEEDED
   và kho confirm đúng một lần; ca thất bại giải phóng đúng một lần khi provider
   thực sự báo trạng thái final. Cần evidence theo order/attempt trong API/DB
   read-only, không chỉ screenshot Return00 hoặc HTTP200 của callback.
8. **Rollout/rollback/cleanup:** task chưa yêu cầu rollout. Giữ PaymentMode
   sandbox và endpoint IPN khi còn pending; không đổi none/xóa order/hạ Flyway/
   reset DB. Dọn cart/test session qua API hợp lệ sau khi ghi evidence, không
   xóa lịch sử tài chính/kho hoặc sửa tay trạng thái pending. Không push/merge
   hoặc gửi email hỗ trợ khi chưa có ủy quyền cho hành động đó.

## Tình trạng kiểm tra ngày 25/10/2026 (UTC+7)

- Working tree ban đầu sạch; `origin/main` fetch vẫn `847b093`. Nhánh mới
  `feature/payment/vnpay-sit-readiness` dựa trên `570922c` để khớp source đã
  triển khai; chưa push/merge. Schema source mới nhất V26, không thêm migration.
- CLI2.37.9 có sẵn nhưng phiên `shop-prod` hết hạn. Người dùng cho phép
  `aws login --profile shop-prod --region ap-southeast-1`; login thành công,
  STS xác nhận đúng account Shop. Không cài tool/đổi quyền hoặc secret.
- HTTP read-only15:15: UI200; Catalog200/totalElements12; anonymous
  `/api/auth/my-info`401. Ba request có header CloudFront, không tạo giao dịch.
- Snapshot AWS15:18: stack UPDATE_COMPLETE, mode vnpay-sandbox, revision
  trusted-proxy hiện hành; ASG2/2/4, hai AZ cùng LT7/InService/Healthy, đúng
  hai target Healthy, refresh gần nhất Successful. Không restart/rollout.
- Người dùng yêu cầu kiểm tra đăng ký IPN. Đã đọc metadata thư và thông báo
  delivery failure trong đúng Gmail của merchant, tìm cả spam/trash bằng
  query giới hạn ngày và tên miền provider; không đọc lại body chứa HashSecret,
  không tải attachment hoặc ghi nội dung thư/PII vào repo.
- Kết quả email: yêu cầu lúc16:52 ngày24/10 gửi tới `no-reply@vnpay.vn`.
  Yêu cầu khác lúc16:55 tới `support.vnpayment@vnpay.vn` bị Mail Delivery
  Subsystem trả **550 5.1.1 — tài khoản email không tồn tại**. Có bản nháp
  chưa gửi cùng người nhận. Không sửa/gửi lại/xóa thư hoặc draft.
- Tìm thư đến từ các tên miền VNPay từ24/10 chưa thấy kết quả xác nhận mới.
  Đây không chứng minh chắc chắn merchant chưa cấu hình qua kênh khác; phải
  kiểm tra merchant hoặc nhận xác nhận hỗ trợ. Email đăng ký tài khoản ngày
  19/10 không phải xác nhận IPN.
- Gmail đã truy cập được bằng kết nối hiện hành; không yêu cầu cài/kết nối
  lại. Người dùng đã tự đăng nhập merchant; dashboard đúng tên Shop, bộ lọc
  19–25/10 hiển thị tổng0 VND. Không dùng con số này để kết luận chắc chắn
  chưa từng có order/attempt hoặc IPN đã cấu hình.
- Đã kiểm tra menu Thông tin tài khoản và Cài đặt thông báo. Trang đầu có
  danh sách nhiều website sandbox; không thấy mã merchant Shop trong trang
  hiện tại. Không mở chi tiết website khác, không thay ID/đoán URL để truy
  cập, không copy danh sách hoặc thông tin liên hệ không liên quan vào repo.
  Cài đặt thông báo chỉ có trường Email/Đăng ký, không phải cấu hình IPN;
  không điền/gửi form. Chưa có bằng chứng URL IPN của Shop trong portal.
- Agent không đọc/lưu/đổi password, không gửi email hoặc thay cài đặt merchant.
  Kênh Trợ giúp/Liên hệ được portal công khai trỏ tới trang tư vấn chính thức
  `https://vnpay.vn/dang-ky-tu-van/`. Cần xác minh đúng nơi tiếp nhận sandbox
  trước khi truyền thông tin; không tự chọn người nhận từ danh sách website.

**Chưa nghiệm thu PAY-SIT-01C:** chưa xác minh đăng ký IPN, chưa có USER
session USER dùng test, chưa tạo order/attempt/giao dịch sandbox trong task này.
Không đóng M5 hoặc coi health/receipt97 là E2E thành công.

## Trình tự tiếp tục

1. Chủ merchant đã tự đăng nhập
   [VNPay Merchant sandbox](https://sandbox.vnpayment.vn/merchantv2/Users/Login.htm)
   trong trình duyệt ứng dụng. Khi phiên hết hạn, tự đăng nhập lại; không gửi
   mật khẩu/OTP/ảnh credential vào chat.
2. Agent kiểm tra màn hình thực tế theo quyền chỉ đọc. Chỉ xác nhận IPN nếu
   có cấu hình hoặc phản hồi rõ ràng khớp URL:

   ```text
   https://aydibro.click/api/payments/webhooks/vnpay
   ```

   Nếu portal không có chức năng đó, dùng kênh hỗ trợ **đã xác minh hiện hành**.
   Không tiếp tục gửi vào địa chỉ bounce hoặc mặc định Reply tới no-reply sẽ
   được xử lý. Không đoán địa chỉ email thay thế; thư mới không đính kèm/
   forward email merchant có HashSecret. Tạo/gửi draft cần yêu cầu riêng.
3. Khi gate IPN đạt, kiểm lại fleet/runtime/image/secret flags. Chủ tài khoản
   đăng nhập USER riêng vào website; không dùng ADMIN để thay ownership và
   không đưa token vào chat/command line. Chỉ tạo tối đa một order cho mỗi
   kịch bản đã chọn trước; không retry checkout bằng key mới khi mất response.
4. Ghi baseline order/payment/inventory theo reference được phép trước khi
   tạo giao dịch. Chọn SKU DEMO còn tồn và quantity1; xác minh VND/giá do server
   tính. Chỉ mở handoff HTTPS có allowlist đã kiểm, không lưu URL đầy đủ.
5. Người dùng tự thao tác màn hình thanh toán sandbox bằng thông tin test
   chính thức; không dùng thẻ/tài khoản ngân hàng thật. Agent đối chiếu Order
   API và IPN/inbox/stock read-only, ghi kết quả theo ma trận sau.

| Kịch bản | Bằng chứng cần có | Trạng thái |
| --- | --- | --- |
| Success | IPN thực, đúng chữ ký/amount/reference; Payment SUCCEEDED; Order PAID; một confirm kho | Chưa chạy |
| Failure/cancel | Provider báo final failure, Order/kho chuyển đúng policy; không suy diễn từ đóng tab | Chưa chạy |
| Duplicate | Retry do provider/SIT thực, không thêm transition/inbox/movement | Chưa chạy |
| Return giả00 | Không đổi Order/stock khi chưa có IPN hợp lệ; tách evidence local/live | Chưa chạy live |
| Missing/late IPN | Dừng tạo thêm order, ghi pending/manual action, không sửa DB/giả success | Chưa chạy |

6. Nếu thiếu bằng chứng, báo đúng phần chưa đạt. Kill switch query/worker/
   recovery/refund vẫn giữ như baseline; không bật thêm để làm test xanh.
   Không nâng quyền hoặc dump DB/log thô để thu evidence.
7. Logout/revoke phiên test khi xong; xử lý cart và đơn còn pending theo API/
   quy trình nghiệp vụ, không xóa ledger/inbox/history. Ghi kết quả vào README.

## Phân biệt các loại bằng chứng

- **Metadata/health:** hệ thống có cấu hình và đang chạy, chưa có giao dịch.
- **Local regression:** callback ký bằng fixture, MySQL/H2; không phải VNPay gọi.
- **Provider SIT:** VNPay gửi callback theo kịch bản, có đối chiếu đơn/kho.
- **Go-live:** còn gate bảo mật/backup/outbox/credential và tiền thật riêng.

Guard34 finding và thiếu durable CloudTrail là baseline chưa được sửa;
coverage branch backend chưa được đo lại ở task tài liệu/readiness này và
chưa có bằng chứng đạt mục tiêu >80%. Không tạo thêm tài nguyên AWS.

Nguồn giao thức: [VNPay PAY — IPN server-to-server](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
