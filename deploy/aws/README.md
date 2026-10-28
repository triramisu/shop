# AWS prod — cấu hình và thứ tự triển khai

[Mục lục triển khai](../README.md) · [Tài liệu](../../docs/README.md)

## Các file đang dùng

| File | Vai trò |
| --- | --- |
| [shop-prod.yaml](shop-prod.yaml) | Template CloudFormation duy nhất: VPC/subnet/NAT, ALB, ASG, RDS, S3, CloudFront và IAM ứng dụng |
| [application.env.example](application.env.example) | Mẫu env-file text cho application secret; giá trị thật lưu kín trong Secrets Manager |
| [iam/backend-github-trust.json](iam/backend-github-trust.json) | Khóa GitHub OIDC vào repository/environment đã chọn |
| [iam/backend-github-permissions.json](iam/backend-github-permissions.json) | Giới hạn quyền release của GitHub backend |
| [PROD.md](PROD.md) | Hồ sơ rollout có ngày, lý do và bằng chứng; không phải trạng thái AWS live mới |
| [.gitignore](.gitignore) | Loại env, parameters local và key riêng khỏi Git |

Template/policy có giá trị riêng của Shop. Đối chiếu account, Region, resource
ARN, image registry và repository trước khi dùng cho dự án khác.

## Kiến trúc giữ nguyên

Browser → CloudFront/WAF → S3 UI hoặc ALB → EC2 private trong ASG → RDS/S3 ảnh.
Một NAT, RDS Single-AZ và ASG 2–4 máy là mức công suất hiện tại; chạy profile
prod không tự biến cấu hình này thành HA đầy đủ.

Template này nhận `EdgeWebAclArn` để gắn ACL. Rule WAF thuộc stack edge riêng;
source WAF chưa đồng bộ vào checkout này, cần lấy đúng baseline trước update.
RDS dùng tài khoản runtime/migration riêng và TLS; không dựng MySQL/MinIO trên app EC2.

## Thứ tự dựng mới

1. Kiểm đúng account/Region, domain, ACM origin/viewer, quota, key pair và AMI Ubuntu đã pin.
2. Review template/parameters và gate hạ tầng, tạo change set ở phase `foundation`.
3. Foundation xong: lấy Outputs, cấu hình DNS origin, tạo hai tài khoản RDS và Store application secret.
4. Build/scan/push image, lấy digest; update phase `application` với secret ARN và viewer HTTPS đã chuẩn bị.
5. Chờ app/ALB Healthy ở hai AZ; kiểm API public/private, DB/storage và UI HTTPS.
6. Trial rồi vận hành phát hành image bằng [ASG CD](../../docs/asg-cd.md); payment theo gate riêng ở [VNPay SIT](../../docs/vnpay-sit.md).

Hai phase trên đều thuộc prod; không còn template hoặc parameters lab.
`LatestAmiId` nhận AMI ID cụ thể. `ApplicationImage` nhận digest, không nhận
`latest`. `ApplicationEnvSecretArn` chỉ là ARN; không điền nội dung secret vào parameters.

Template vượt giới hạn inline 51.200 byte: dùng upload Console hoặc artifact
S3 private/TemplateURL. Giữ nguyên parameter không định đổi và review plan
trước Execute. Không lấy số LT/instance hoặc trạng thái trong hồ sơ cũ làm
baseline mới mà chưa kiểm lại.

## Bằng chứng kiểm tra và giới hạn nghiệm thu

CI chạy `ci/aws-prod-template.test.cjs` cho ranh giới network/data/edge; các
suite VNPay/trusted-proxy kiểm bootstrap thực bằng fixture và cú pháp Bash/Python.
CI còn validate `compose.yaml` ở gốc cho dev. Test không tạo tài nguyên AWS.

Các test này không thay cfn-lint, Guard hoặc AWS pre-deployment validation
của một đợt thay đổi hạ tầng. Khi chuẩn bị deploy phải ghi riêng từng gate,
review finding còn lại và hậu kiểm runtime; CI xanh không đồng nghĩa go-live.

Luồng bootstrap Ubuntu dùng user `ubuntu` cho SSH nếu cần; quản trị qua SSM
không cần public IP cho app. Xem [ASG CD](../../docs/asg-cd.md),
[trusted proxy](../../docs/trusted-proxy.md) và [S3 sản phẩm](../../docs/product-images-s3.md)
cho hướng dẫn theo chủ đề.
