# AWS prod — template và hồ sơ triển khai

[Mục lục triển khai](../README.md) · [Chọn file AWS](README.md) · [Mục lục tài liệu](../../docs/README.md)

## Bắt đầu theo việc cần làm

| Bạn cần làm gì? | Nguồn cần đọc |
| --- | --- |
| Hiểu hạ tầng prod-profile | [`shop-prod.yaml`](shop-prod.yaml) và các parameter/rule được khai báo trong file |
| Chuẩn bị cấu hình app | [`application.env.example`](application.env.example); giữ giá trị thật trong secret của môi trường |
| Phát hành image backend qua cả cụm ASG | [Runbook ASG CD](../../docs/asg-cd.md) |
| Kiểm tra thanh toán sandbox thực tế | [VNPay SIT](../../docs/vnpay-sit.md) |
| Xem tiến độ mới nhất đã ghi nhận | [README dự án](../../README.md#2-trạng-thái-hiện-tại) |

Phần dưới là hồ sơ PAY-SIT/trusted-proxy có ngày; giữ để truy vết quyết định
và kết quả lúc triển khai. LT version, test count và trạng thái SIT trong đoạn
cũ không thay cho bằng chứng mới hơn của task CD/SIT hoặc kiểm tra AWS live.

## Prod-profile CloudFormation candidate

`shop-prod.yaml` được khôi phục từ Original template của stack đang chạy,
không sao chép bản draft trong checkout vận hành. Bản nền VNPay sandbox đã
deploy; **trusted-proxy đã Execute và hậu kiểm triển khai đạt**. Source AWS
đã đối chiếu đúng SHA bên dưới; việc này không chứng minh SIT đạt.

Ngày 24/10/2026 khoảng 16:46 UTC+7, bản nền SHA-256 `19ef36d981b748db7975ca52ae161af40571891a87fdd7070bf1b0f63e3c6d19` đã được Execute với
`PaymentMode=vnpay-sandbox`: stack UPDATE_COMPLETE, refresh Successful,
hai instance LT version 6 và hai ALB target Healthy. Container/secret parity
và IPN từ chối checksum giả đã được kiểm tra. Sau đó người dùng đã trả lời
email đăng ký IPN; chưa có bằng chứng merchant xác nhận hoặc giao dịch sandbox,
chưa go-live. Bằng chứng và lệnh nằm trong runbook.

Candidate PAY-SIT-01B hiện tại có SHA-256
`319e79f59c257c20c7d4fb2d12f70d82b9dedafe0e78e19c04e703d209d6e7b9`,
52.108 byte; đã upload đúng checksum lên artifact bucket CloudFormation
private/mã hóa sẵn có. Change set `trusted-proxy-20261024T101541Z` đã được
người dùng duyệt và Execute lúc 19:35:53 UTC+7 ngày 24/10. Snapshot20:13:20
xác nhận UPDATE_COMPLETE/refresh Successful, hai máy LT7/targets Healthy.
Hậu kiểm20:23–20:24 xác nhận container/native flags/payment flags/secret
parity, AMI và mọi LT data ngoài UserData giữ nguyên, SG/origin/WAF/API
forwarding giữ nguyên. Chưa có giao dịch SIT thật. Plan
mặc định 5 Modify (ASG/policy Conditional), enhanced 3 Modify; không
Add/Remove. Không được hiểu kết quả enhanced là bỏ qua rủi ro của plan mặc định.
Đọc [runbook trusted-proxy](../../docs/trusted-proxy.md) trước lần thay đổi
tiếp theo; không Execute lại change set đã dùng.

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
