# Triển khai Shop

[README dự án](../README.md) · [Mục lục tài liệu](../docs/README.md)

Thư mục này chứa cấu hình hạ tầng và công cụ triển khai backend. Chọn đúng
mô hình ở bảng dưới trước khi mở file hoặc chạy lệnh.

## Chọn mô hình triển khai

| Bạn cần làm gì? | Bắt đầu ở đâu? | Thành phần chính |
| --- | --- | --- |
| Vận hành AWS hiện tại | [Template prod và hồ sơ triển khai](aws/PROD.md), sau đó [CD qua ASG](../docs/asg-cd.md) | CloudFormation, ALB, ASG, RDS, S3, CloudFront |
| Chạy backend trên một máy bằng Docker Compose | [Runbook single-host](../docs/runbook/ci-cd.md) | `compose.prod.yml`, `deploy.sh`, MySQL và MinIO trong Docker |
| Dựng lab Docker để đo tải có kiểm soát | [Hướng dẫn lab](lab/README.md) | nginx/UI, app, MySQL, MinIO; giới hạn tài nguyên container |
| Chạy dev trên máy cá nhân | [Chạy dự án](../README.md#9-chạy-dự-án) và [object storage local](../docs/runbook/local-object-storage.md) | `compose.yaml` ở gốc repo và Spring profile `dev` |

AWS hiện tại phát hành backend bằng **Launch Template + ASG Instance Refresh**.
`deploy.sh` là công cụ cho mô hình một máy, không phải đường CD của ASG.
Từ `prod` trong tên Compose không có nghĩa Compose đó chính là kiến trúc AWS đang chạy.

## Cấu trúc thư mục

```text
deploy/
├── README.md                    ← mục lục này
├── .env.prod.example            ← mẫu biến cho Compose một máy
├── compose.prod.yml             ← app + MySQL + MinIO trên một host
├── deploy.sh                    ← đổi image, kiểm health và rollback trên host đó
├── aws/                         ← hạ tầng AWS và mẫu cấu hình runtime
│   ├── README.md                ← chỉ dẫn chọn file và lịch sử lab AWS
│   ├── PROD.md                  ← hướng dẫn/bằng chứng của template prod
│   ├── shop-prod.yaml           ← baseline hạ tầng AWS prod-profile
│   ├── shop-lab.yaml            ← template lab AWS trước đây
│   ├── application.env.example  ← mẫu nội dung application secret
│   ├── parameters.example.json  ← parameters mẫu của template lab cũ
│   ├── .gitignore               ← loại env/key/parameters local khỏi Git
│   └── iam/                     ← trust/permissions cho GitHub deploy backend
├── lab/                         ← lab Docker riêng, khác lab hạ tầng AWS
│   ├── README.md
│   ├── .env.lab.example
│   ├── compose.lab.yml
│   └── setup-ubuntu.sh
└── mysql/
    └── init/
        └── 01-create-accounts.sh ← tạo user runtime/migration cho MySQL Compose
```

## Các thư mục con dùng để làm gì?

| Đường dẫn | Vai trò | Khi cần mở |
| --- | --- | --- |
| [`aws/`](aws/README.md) | CloudFormation cho VPC/subnet, ALB, ASG, RDS, S3, CloudFront và cấu hình liên quan | Dựng hoặc thay đổi hạ tầng AWS |
| `aws/iam/` | Hai policy [trust](aws/iam/backend-github-trust.json) và [permissions](aws/iam/backend-github-permissions.json) của role backend CD | Review GitHub OIDC, quyền deploy và dependency reads |
| [`lab/`](lab/README.md) | Chạy cả stack trong lab Docker; UI source nằm ở repo riêng | Đo tải/so sánh cấu hình nginx trên môi trường lab đã chọn |
| `mysql/init/` | [Script tạo tài khoản](mysql/init/01-create-accounts.sh) cho MySQL container | Khởi tạo volume MySQL mới của Compose single-host |

Script init MySQL được `compose.prod.yml` mount vào container. Nó không tự
tạo user trên RDS và không tự chạy lại mỗi lần restart một volume đã có dữ liệu.

## Các file ở gốc `deploy/`

| File | Vai trò | Đọc cùng |
| --- | --- | --- |
| [`.env.prod.example`](.env.prod.example) | Danh sách biến cần điền cho Compose single-host; bản mẫu không phải secret đang chạy | [Runbook single-host](../docs/runbook/ci-cd.md) |
| [`compose.prod.yml`](compose.prod.yml) | Khai báo ba service app/MySQL/MinIO, volume, health và port trên một host | Script init MySQL và file env của đúng môi trường |
| [`deploy.sh`](deploy.sh) | Nhận image của Shop, pull/chạy app, kiểm health và thử quay lại image trước nếu lỗi | Bundle single-host tại `/opt/shop` |

## Ba điểm dễ chọn nhầm

1. **Hai loại lab:** `lab/compose.lab.yml` là lab Docker; `aws/shop-lab.yaml`
   là template hạ tầng AWS lịch sử. Hai bộ này không dùng chung cách bootstrap.
2. **Hai loại env mẫu:** `.env.prod.example` phục vụ Compose;
   `aws/application.env.example` phục vụ bootstrap ứng dụng trên AWS. Không
   truyền một file sang mô hình khác chỉ vì cùng chạy Spring profile `prod`.
3. **Hai template AWS:** `parameters.example.json` còn tên `LabClientIpv4` và
   AMI dạng SSM path của lab. `shop-prod.yaml` dùng `AdditionalClientIpv4` và
   AMI ID cụ thể; không nạp nguyên file parameters lab vào prod.

`shop-prod.yaml` nhận ARN để gắn WAF qua `EdgeWebAclArn`; nó không chứa toàn bộ
rule của stack edge WAF. Checkout này chưa có template WAF riêng đã được đồng bộ
vào `main`; kiểm đúng source/stack trước khi chuẩn bị thay đổi WAF.

## Đọc và cập nhật cấu hình

- Từ mục lục này chọn mô hình → đọc runbook → kiểm source/parameters đúng release
  → review kế hoạch → triển khai → hậu kiểm.
- Các đoạn có ngày, change set hoặc test count là bằng chứng tại thời điểm ghi.
  Trạng thái mới hơn xem ở [README dự án](../README.md#2-trạng-thái-hiện-tại)
  và runbook của task tương ứng; không dùng snapshot cũ như trạng thái AWS live.
- Cấu hình mẫu giữ placeholder; password/token/merchant key nằm trong cấu hình
  kín của môi trường. Không lưu giá trị thật vào README, YAML hoặc policy mẫu.
- Giữ đường dẫn ổn định: workflow, Docker Compose, script và test đang tham chiếu
  các file này. Muốn đổi tên/di chuyển phải sửa các nơi gọi trong cùng thay đổi.
