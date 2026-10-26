# S3-01 — Kho ảnh sản phẩm trên AWS

[Mục lục tài liệu](README.md) · [Triển khai](../deploy/README.md)

## 1. Mục tiêu và phạm vi

Thêm S3 adapter cho Catalog để mọi EC2 trong ASG dùng cùng kho ảnh private;
giữ MinIO cho dev/local và giữ nguyên API upload/đọc/xóa ảnh. Không triển khai
AWS, không chuyển/xóa ảnh MinIO, không sửa Payment hoặc tạo CI/CD trong task.

## 2. Sở hữu và phụ thuộc

Catalog sở hữu metadata/object key, giao tiếp qua `ObjectStorage`. Không đổi
schema hoặc migration V1–V25. S3 general-purpose bucket riêng, cùng region
VPC endpoint, tách UiBucket; EC2 nhận quyền qua instance role. Source/layout
và refund đang có thay đổi local từ trước, phải giữ nguyên và không tự gom
vào commit. Không xác nhận baseline đã đồng bộ main hoặc CI đã xanh.

## 3. Hành vi và bất biến

Upload vẫn qua validation/authorization của API hiện có. Object key giữ prefix
`catalog/products/`; PUT dùng `If-None-Match: *`, không ghi đè object đang có.
DELETE lũy đẳng theo semantics S3, không xóa version lịch sử. URL GET ký có TTL
mặc định 15 phút, tối đa một giờ; URL không lưu trong DB. Mọi app dùng chung
bucket/key; không fallback qua MinIO khi S3 lỗi và không auto-create bucket.

## 4. Bảo mật và resilience

Bucket Block Public Access, SSE-S3, versioning, retain, TLS-only; IAM chỉ có
HeadBucket/ListBucket và Get/Put/DeleteObject trong prefix ảnh. Không cấp ACL,
CreateBucket, DeleteBucket hoặc xóa version. Không ép mọi GET phải qua VPC
endpoint vì browser tải presigned URL từ Internet. URL ký là bearer credential:
không log, không đưa vào Git; hết hạn credential tạm có thể rút ngắn TTL thực.
SDK có connect/read/API timeout hữu hạn, một attempt (không retry mutation
ngầm), circuit breaker `catalogS3` riêng và metric tag hữu hạn. Không lộ lỗi
SDK/credential trong response hoặc health. AWS bootstrap không nhận static
AWS credential/endpoint override từ application secret; region/bucket do stack
inject. MinIO credentials không bắt buộc khi chọn S3.

## 5. Đầu ra

AWS SDK v2 được pin; S3 properties/client/adapter, test unit + HTTP contract,
config theo môi trường, CloudFormation bucket/IAM/bootstrap/Outputs, test
hạ tầng và runbook cutover/rollback. Không đánh dấu cloud smoke đạt từ mock.

## 6. Nghiệm thu chức năng

PUT đúng bytes/content type/conditional header; duplicate 412 không overwrite;
GET URL ký đúng bucket/key/TTL; DELETE lặp; HeadBucket fail startup; missing
bucket/403/500/timeout; timeout không retry mù; circuit mở/half-open/recover;
key ngoài prefix và cấu hình sai bị từ chối; S3 profile không cần MinIO;
MinIO/local regression, upload validation/rollback, Modulith/ArchUnit giữ nguyên.

## 7. Gate và bằng chứng

Chạy test có liên quan, Maven verify, Spotless, JaCoCo, git diff --check;
Node AWS template tests (Bash/Python guard), CloudFormation schema/validate
nếu credential có sẵn. Không tạo AWS resource để chạy gate. SDK HTTP contract
không thay thế smoke S3/IAM/VPC endpoint thật. Ghi rõ test MySQL/MinIO thực
chạy hay skipped, không gọi skipped là pass.

Lệnh chạy lại tại root backend (Java 21, Docker Engine đang chạy):

```powershell
./mvnw.cmd --batch-mode clean verify
node --test ci/aws-prod-template.test.cjs ci/aws-vnpay-template.test.cjs ci/aws-trusted-proxy.test.cjs
git diff --check
```

Node checks cần `js-yaml`, Bash và Python; cấu hình `SHOP_YAML_MODULE`,
`SHOP_BASH`, `SHOP_PYTHON` theo
[runbook AWS](../deploy/aws/README.md#bằng-chứng-kiểm-tra-và-giới-hạn-nghiệm-thu).
Không cài các công cụ kiểm tra vào image runtime. SDK chỉ sử dụng
`url-connection-client`; loại các HTTP transport Apache/Netty không dùng.

Template prod vượt giới hạn inline; khi chuẩn bị thay đổi AWS phải chạy các
gate hạ tầng riêng và dùng artifact S3/TemplateURL, không truyền file prod
qua `--template-body`. Bộ template/test lab đã được bỏ ngày 27/10/2026.

Bằng chứng local ngày 18/10/2026:

- Sau khi Docker Engine hoạt động, full `clean verify` đạt **477 test,
  0 failure/error/skipped**, gồm MySQL 8.0.46 và MinIO/Silo Testcontainers,
  Flyway fresh/upgrade V1–V25, Spring Modulith/ArchUnit, OpenAPI, Spotless và
  JaCoCo. Lần đầu khi Docker chưa chạy thất bại do môi trường, không tính là pass.
- Coverage toàn backend: instruction **91,26%**, line **91,06%**, branch
  **69,91%**; không đạt mục tiêu branch cuối dự án trên 80%, không đổi gate
  hoặc loại mã nghiệp vụ để làm đẹp coverage. Riêng package S3: line **100%**,
  branch **98,81%** (83/84 nhánh).
- 18 test S3 mới đạt; chạy lại `verify -Dtest=S3ObjectStorage*Tests` sau khi
  rà dependency và bổ sung kiểm tra URL ký không yêu cầu header browser không
  thể tự gửi. Các lượt chạy riêng không được cộng thêm vào số 477 test.
- **20/20** Node AWS checks, không skipped, có Bash/Python guard và đối chiếu
  AWS resource specification; AWS `validate-template` đạt `CAPABILITY_IAM`.
- Ba Compose config (local/prod/lab) và `git diff --check` đạt. Không sửa
  credential thật, DB/volume local, không chạy Sonar hoặc scan milestone.

## 8. Rollout và rollback

Đầu tiên xuất bản image hỗ trợ cả hai adapter, giữ storage cũ. Khi có quyền
triển khai: tạo bucket/policy, backup/copy ảnh giữ đúng object key và metadata,
đối chiếu count/size/checksum thích hợp (không mặc định ETag là MD5), test URL
từ browser ngoài VPC và PUT/GET/DELETE từ EC2. Tạm khóa upload/delete lúc đồng
bộ cuối; chỉ chuyển ASG sang S3 sau khi nghiệm thu và thay từng node. Không
chạy hỗn hợp hai storage độc lập khi còn mutation. Sau cutover có ảnh mới,
rollback phải dừng mutation và đồng bộ ngược trước; không chỉ đổi flag hoặc
quay về image cũ. Giữ MinIO và bucket retained cho tới khi xác nhận đủ dữ liệu.

Checklist AWS khi đã được phép triển khai, dùng sản phẩm/ảnh kiểm thử riêng:

- Startup trên EC2 dùng instance role, không đưa access key vào secret;
  `HeadBucket` thành công và app health UP.
- Upload qua API có authorization/validation; đối chiếu bytes/content type,
  không bỏ qua API bằng cách ghi ảnh nghiệp vụ trực tiếp vào bucket.
- Đọc cùng ảnh qua hai app node; mở URL ký từ browser ngoài VPC, kiểm tra URL
  hết hạn bị từ chối. Không lưu URL ký hoặc session token vào log/báo cáo.
- Xác nhận traffic S3 cùng region từ app đi Gateway Endpoint; đường đọc của
  browser không cần đi endpoint. Bucket UI không được app role ghi ảnh vào.
- Xóa ảnh fixture qua API, xác nhận GET không còn trả ảnh hiện hành; version
  cũ vẫn chịu lifecycle 30 ngày. Đây không phải cơ chế backup vô thời hạn.
- Không xóa kho MinIO, bucket, version lịch sử hoặc ảnh thật để chạy smoke.

Tham chiếu chính thức: [S3 conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html),
[presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)
và [S3 Gateway Endpoint](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html).

## Trạng thái

Đã nghiệm thu phần source/config/test local; chưa nghiệm thu AWS/IAM/endpoint
thật, chưa push/merge/CI/deploy hoặc migration ảnh. Trước rollout vẫn phải
kiểm chứng MySQL 8.4 và các điều kiện ASG/shared state trong runbook AWS.
