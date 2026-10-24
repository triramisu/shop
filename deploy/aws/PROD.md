# Prod-profile CloudFormation candidate

`shop-prod.yaml` được khôi phục từ Original template của stack đang chạy,
không sao chép bản draft trong checkout vận hành. Đây là candidate VNPay
sandbox; file tồn tại trong Git không chứng minh AWS đã Execute hoặc SIT đạt.

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
- Sau khi có payment pending, không rollback bằng `PaymentMode=none` trước
  khi drain/đối soát vì sẽ bỏ endpoint IPN. Giữ dữ liệu, không hạ Flyway.

README lab trong thư mục này mô tả các lần nghiệm thu cũ; không lấy thành
công local hoặc snapshot lịch sử thay cho trạng thái live hiện tại.
