# Triển khai AWS prod

[README dự án](../README.md) · [Mục lục tài liệu](../docs/README.md)

Repo chỉ giữ một bộ triển khai: **AWS prod qua CloudFormation và ASG**.
Backend chạy trong EC2 private, dùng RDS MySQL và S3; UI nằm ở repo riêng,
build thành static assets để đưa lên S3/CloudFront.

## Cấu trúc

```text
deploy/
├── README.md                   ← bắt đầu ở đây
└── aws/
    ├── README.md               ← cấu hình, thứ tự dựng và kiểm tra AWS
    ├── PROD.md                 ← hồ sơ triển khai có ngày
    ├── shop-prod.yaml          ← template hạ tầng prod duy nhất
    ├── application.env.example ← mẫu application secret
    ├── .gitignore              ← bỏ qua env/key/parameters local
    └── iam/
        ├── backend-github-trust.json       ← GitHub nào được assume role
        └── backend-github-permissions.json ← role được làm gì trên AWS
```

## Đọc theo việc cần làm

| Công việc | Tài liệu/source |
| --- | --- |
| Hiểu và dựng hạ tầng | [AWS README](aws/README.md), [template prod](aws/shop-prod.yaml) |
| Chuẩn bị biến runtime | [Env mẫu](aws/application.env.example); lưu giá trị thật trong Secrets Manager |
| Review IAM cho GitHub backend | [Trust](aws/iam/backend-github-trust.json), [permissions](aws/iam/backend-github-permissions.json) |
| Phát hành code lên cả cụm ASG | [Runbook ASG CD](../docs/asg-cd.md) |
| Kiểm tra VNPay sandbox | [VNPay SIT](../docs/vnpay-sit.md) |
| Tra lại hồ sơ rollout | [PROD.md](aws/PROD.md), [README dự án](../README.md#2-trạng-thái-hiện-tại) |

Luồng phát hành: PR → CI → build/scan/push image → OIDC → CloudFormation cập
nhật image trong Launch Template → ASG Instance Refresh → kiểm toàn fleet.
Máy scale-out sau đó dùng cấu hình/image từ Launch Template đã cập nhật.

## Phạm vi chuẩn hóa AWS-PROD-ONLY — 27/10/2026

1. Mục tiêu: người vận hành có một cách deploy AWS prod rõ ràng; bỏ bộ lab và single-host cũ.
2. Sở hữu: source triển khai, CI và tài liệu; Java/API/schema không đổi.
3. Bất biến: giữ nguyên template prod, IAM, CD controller và cấu hình app; dev local vẫn dùng Compose ở gốc.
4. Dữ liệu/secret: không đọc env thật, không gọi AWS, không xóa stack, database hoặc Docker volume.
5. Đầu ra: chỉ `aws/` trong deploy, hướng dẫn nhất quán và CI kiểm đúng template prod.
6. Ca kiểm: đường dẫn cũ không còn được gọi, dev Compose vẫn validate, prod giữ ranh giới network/data/edge.
7. Gate: test Node của prod/release/bootstrap, workflow lint, liên kết Markdown, diff và SHA-256 file được bảo toàn.
8. Phát hành/rollback: [PR #54](https://github.com/triramisu/shop/pull/54) phải qua `verify` và `dependency-review`, rồi kiểm CI main và CD theo các cờ hiện hữu. Rollback source bằng revert PR; không hoàn tác dữ liệu hoặc tự đổi cờ CD.

`dev` và `prod` là Spring profiles. `foundation` và `application` là hai bước
bootstrap **cùng một hạ tầng prod**, vẫn cần khi dựng mới. Bộ deployment lab
và Compose prod một máy đã bỏ; lịch sử của chúng còn trong Git.
