# Tài liệu Shop

[README dự án](../README.md) · [Mục lục triển khai](../deploy/README.md)

Chọn tài liệu theo việc cần làm. Các file giữ nguyên đường dẫn để những liên
kết trong README, runbook và lịch sử release tiếp tục dùng được.

## Đọc từ đâu?

| Bạn đang muốn biết gì? | Tài liệu bắt đầu |
| --- | --- |
| Dự án đã làm đến đâu? | [Trạng thái hiện tại](../README.md#2-trạng-thái-hiện-tại) |
| AWS prod có những file triển khai nào? | [Mục lục triển khai](../deploy/README.md) |
| GitHub đưa code lên cả cụm ASG ra sao? | [Backend CD qua ASG](asg-cd.md) |
| Test mua hàng và VNPay trên website thế nào? | [VNPay SIT](vnpay-sit.md) |
| API Catalog/tồn kho có contract gì? | Nhóm [kiến trúc và nghiệp vụ](#kiến-trúc-và-nghiệp-vụ) bên dưới |
| Tài liệu nào chỉ ghi quyết định cũ? | Nhóm [kế hoạch và lịch sử cấu trúc](#kế-hoạch-và-lịch-sử-cấu-trúc) bên dưới |

## Cấu trúc tài liệu

```text
docs/
├── README.md       ← mục lục này
├── architecture/   ← domain, API, audit, tìm kiếm và concurrency tồn kho
├── quality/        ← ma trận kiểm thử Identity
├── runbook/        ← chỉ dẫn CI/CD AWS và thao tác object storage local
└── *.md            ← contract tính năng, payment, ASG CD và quyết định dự án
```

Các bảng sau phân nhóm theo chủ đề; một tài liệu có thể chứa cả contract,
bước thao tác, kết quả kiểm tra và lịch sử triển khai của chủ đề đó.

## Triển khai và vận hành

| Tài liệu | Nội dung chính | Cách sử dụng |
| --- | --- | --- |
| [Backend CD qua ASG](asg-cd.md) | GitHub Actions/OIDC, build/scan image, change set, Instance Refresh, kiểm fleet và rollback | Đường phát hành AWS hiện tại |
| [Trusted client IP](trusted-proxy.md) | CloudFront → ALB → backend, header IP và chống giả mạo | Đọc khi sửa proxy/network hoặc điều tra IP |
| [Kho ảnh sản phẩm S3](product-images-s3.md) | Adapter lưu ảnh, bucket/prefix/IAM và checklist cutover | Phân biệt ảnh AWS với MinIO local |
| [Object storage local](runbook/local-object-storage.md) | Khởi động và cấu hình MinIO/Silo ở dev | Dùng khi chạy local |
| [Mục lục CI/CD AWS](runbook/ci-cd.md) | Điểm bắt đầu cho workflow AWS prod và liên kết runbook ASG | Bundle lab/single-host đã được bỏ |

## Thanh toán và VNPay

| Tài liệu | Nội dung chính |
| --- | --- |
| [VNPay sandbox checkout và IPN](vnpay-sandbox.md) | Contract provider, ký/xác minh checksum và xử lý IPN |
| [Preflight VNPay HTTPS](vnpay-sandbox-preflight.md) | Chuẩn bị secret, provider flags và điều kiện rollout |
| [VNPay SIT qua website](vnpay-sit.md) | Các ca kiểm thử, cấu hình merchant/kho, bằng chứng kết quả và ca chưa nghiệm thu |
| [Thanh toán trên UI](frontend-payment.md) | Link chuyển VNPay và cách UI đọc trạng thái thanh toán |
| [Status query](vnpay-status-query.md) | Truy vấn trạng thái giao dịch từ provider |
| [Reconciliation worker](vnpay-reconciliation.md) | Đối soát và xử lý những giao dịch cần kiểm tra lại |
| [Refund core](payment-refund-core.md) | Lõi hoàn tiền, idempotency và giới hạn ngân sách hoàn |

Một phần đã có code không đồng nghĩa switch vận hành đã bật hoặc đã vượt qua
SIT provider thật. Đọc ngày/bằng chứng/phạm vi và các ca còn thiếu trong từng file.

## Kiến trúc và nghiệp vụ

| Tài liệu | Nội dung chính |
| --- | --- |
| [Catalog API](architecture/catalog-api.md) | Endpoint public/quản trị và hợp đồng API |
| [Catalog domain model](architecture/catalog-domain-model.md) | Category, product, variant và invariant |
| [Catalog search](architecture/catalog-search.md) | FULLTEXT/index/query plan và benchmark |
| [Inventory domain model](architecture/inventory-domain-model.md) | Số dư tồn kho và nghiệp vụ kho |
| [Vòng đời reservation](architecture/inventory-reservation-lifecycle.md) | Giữ, xác nhận, nhả và hết hạn tồn kho |
| [Reservation locking](architecture/inventory-reservation-locking.md) | Conditional update/locking và kiểm thử cạnh tranh |
| [Danh mục audit event](architecture/audit-event-catalog.md) | Các loại sự kiện audit và dữ liệu cần ghi |
| [Lịch sử đơn của khách](customer-order-history.md) | API paging/detail và quyền sở hữu đơn hàng |

## Chất lượng và phân quyền

| Tài liệu | Nội dung chính |
| --- | --- |
| [Ma trận test Identity](quality/identity-test-matrix.md) | Các nhóm ca kiểm thử xác thực và quản trị |
| [Ma trận USER/token/ownership](demo-user-authorization-matrix.md) | Kiểm tra quyền theo người dùng và chủ sở hữu tài nguyên |

## Kế hoạch và lịch sử cấu trúc

| Tài liệu | Nội dung chính | Lưu ý khi đọc |
| --- | --- | --- |
| [Kế hoạch hoàn thiện website](website-delivery-plan.md) | Mức ưu tiên và phụ thuộc đã bàn | Quyết định có ngày; trạng thái mới hơn ở README gốc |
| [Xuất bản backend độc lập](backend-publication.md) | Contract tách source/build và gate xuất bản | Không dùng trạng thái “chưa push” của một đoạn cũ để kết luận toàn repo |
| [Backend về gốc repo](backend-root-layout.md) | Lý do bỏ lớp thư mục `backend/`, kiểm chứng và rollback layout | Hồ sơ thay đổi cấu trúc |
| [Tách UI](frontend-separation.md) | UI chuyển sang repo/thư mục riêng và cấu hình build context | Các đường dẫn/snapshot cũ cần đối chiếu layout hiện hành |

## Quy ước đọc và cập nhật

1. **Code/schema/template/workflow** là nguồn sự thật kỹ thuật; **README gốc**
   theo dõi tiến độ. Tài liệu task ghi contract và bằng chứng tại thời điểm nghiệm thu.
2. Chữ “đã test local”, “đã deploy”, “đã SIT” và “đã go-live” là các mức khác nhau.
   Không suy từ một mức sang mức còn lại hoặc coi kế hoạch là việc đã hoàn thành.
3. Khi thêm tài liệu, đặt tên nêu rõ chủ đề; thêm vào đúng nhóm ở mục lục này và
   liên kết từ runbook liên quan. Không nhân bản nội dung dài chỉ để làm mục lục.
4. Khi đổi tên/di chuyển file, sửa mọi relative link/nơi gọi và kiểm lại trong cùng
   thay đổi. Ghi snapshot mới có ngày thay vì xóa bằng chứng lịch sử còn hữu ích.
5. Giữ secret/PII ngoài tài liệu; dùng tên biến hoặc placeholder. Link tới file
   source đã công bố và report phù hợp thay vì dán raw log hoặc credential.
