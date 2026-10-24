# Prod-profile CloudFormation candidate

`shop-prod.yaml` được khôi phục từ Original template của stack đang chạy,
không sao chép bản draft trong checkout vận hành. Bản nền VNPay sandbox đã
deploy; **candidate trusted-proxy hiện tại chưa Execute**. File tồn tại trong
Git không chứng minh AWS đã áp dụng chính bản đó hoặc SIT đạt.

Ngày 24/10/2026 khoảng 16:46 UTC+7, bản nền SHA-256 `19ef36d981b748db7975ca52ae161af40571891a87fdd7070bf1b0f63e3c6d19` đã được Execute với
`PaymentMode=vnpay-sandbox`: stack UPDATE_COMPLETE, refresh Successful,
hai instance LT version 6 và hai ALB target Healthy. Container/secret parity
và IPN từ chối checksum giả đã được kiểm tra. Sau đó người dùng đã trả lời
email đăng ký IPN; chưa có bằng chứng merchant xác nhận hoặc giao dịch sandbox,
chưa go-live. Bằng chứng và lệnh nằm trong runbook.

Candidate PAY-SIT-01B hiện tại có SHA-256
`319e79f59c257c20c7d4fb2d12f70d82b9dedafe0e78e19c04e703d209d6e7b9`,
52.108 byte; đã upload đúng checksum lên artifact bucket CloudFormation
private/mã hóa sẵn có, chưa thay runtime. Change set
`trusted-proxy-20261024T101541Z` đang AVAILABLE, chờ duyệt Execute. Plan
mặc định 5 Modify (ASG/policy Conditional), enhanced 3 Modify; không
Add/Remove. Không được hiểu kết quả enhanced là bỏ qua rủi ro của plan mặc định.
Đọc [runbook trusted-proxy](../../docs/trusted-proxy.md) trước rollout.

Xem [hợp đồng, kết quả và từng bước triển khai VNPay](../../docs/vnpay-sandbox-preflight.md)
và [trạng thái dự án](../../README.md).

- `PaymentMode` mặc định `none`; chỉ `vnpay-sandbox` bật provider và Order
  initiation/event consumer. Refund/query/reconciliation/recovery vẫn tắt.
- `LatestAmiId` giữ tên cũ nhưng nhận **AMI ID cụ thể**, không còn nhận đường
  dẫn SSM. Khi update stack hiện hành, lấy AMI app/bastion đang chạy, xác minh
  chúng khớp và review plan không thay bastion; không dùng Ubuntu latest mù.
- Merchant secret là Plaintext env-file trong Secrets Manager; không điền
  HashSecret, password hoặc token vào template/parameters/Git. Giữ DB/JWT cũ.
- Giữ 1 NAT, RDS Single-AZ, ASG 2–4, private app/DB/S3 và CloudFront/WAF/TLS;
  đây không phải HA production hoặc chứng nhận compliance.
- Không sửa console trực tiếp/ép refresh/terminate instance để né kế hoạch.
  Lint, bootstrap tests, Guard và AWS validation là các gate riêng; Execute
  cần review plan cụ thể. Có thể tạm chạy 3 app khi rollout, phát sinh phí.
- Template hiện vượt 51.200 byte inline: dùng S3 `--template-url` (giới hạn
  1.048.576 byte), không `--template-body file://...` hoặc dán vào editor
  inline. Artifact không nằm trong bucket UI/sản phẩm. Kiểm ownership,
  Block Public Access, SSE/checksum; giữ source phục vụ plan/rollback.
- Trusted proxy chỉ hợp lệ với luồng DNS trực tiếp CloudFront → ALB đã xác
  minh origin secret → app. Không bật Cloudflare proxy rồi mặc định coi
  `event.viewer.ip` vẫn là IP người dùng. Không đưa các override native/header
  vào secret để thay thế LT hoặc trust mọi `X-Forwarded-For`.
- Sau khi có payment pending, không rollback bằng `PaymentMode=none` trước
  khi drain/đối soát vì sẽ bỏ endpoint IPN. Giữ dữ liệu, không hạ Flyway.

README lab trong thư mục này mô tả các lần nghiệm thu cũ; không lấy thành
công local hoặc snapshot lịch sử thay cho trạng thái live hiện tại.
