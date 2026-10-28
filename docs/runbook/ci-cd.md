# CI/CD AWS prod

[Mục lục tài liệu](../README.md) · [Triển khai](../../deploy/README.md)

Đường phát hành hiện tại: **GitHub Actions → OIDC → CloudFormation → ASG
Instance Refresh**. Cấu hình nằm trong [CI](../../.github/workflows/ci.yml) và
[CD](../../.github/workflows/cd.yml). Hướng dẫn đầy đủ ở [ASG CD](../asg-cd.md).

## Luồng

```text
PR → verify + dependency review → merge main → CI main xanh
   → build/scan/push image digest → assume role qua OIDC
   → image-only change set → ASG thay máy tuần tự → kiểm toàn fleet
```

- App private dùng RDS/S3; release cập nhật Launch Template để máy scale-out cũng nhận đúng image.
- [Template prod](../../deploy/aws/shop-prod.yaml) và [IAM](../../deploy/aws/README.md) là source triển khai.
- Credential runtime nằm trong Secrets Manager; GitHub dùng role OIDC thay AWS key tĩnh.
- Pipeline UI thuộc repo UI riêng, đưa static assets lên S3/CloudFront.

Bộ Compose single-host, `deploy.sh`, init MySQL container và deployment lab
đã được bỏ ngày 27/10/2026 theo quyết định chỉ giữ AWS prod. Các lệnh/cờ CD
single-host cũ còn trong Git history, không còn là hướng dẫn vận hành hiện hành.
Dev local vẫn dùng [Compose ở gốc](../../compose.yaml) và [object storage local](local-object-storage.md).
