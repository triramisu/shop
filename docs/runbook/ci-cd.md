# CI/CD single-host lịch sử

**Luồng AWS production hiện tại dùng RDS/S3 + ASG:** xem
[CD-ASG-01](../asg-cd.md). Không dùng deploy.sh/Compose dưới đây cho ASG.
Nội dung còn lại là hướng dẫn single-host trước đây, không phải workflow CD hiện hành.

## Phạm vi

Pipeline này chỉ triển khai backend modular monolith lên một máy Ubuntu đã được quản lý bởi AWS Systems Manager. UI S3, CloudFront/Cloudflare, ALB, VPC, DNS và việc mua tên miền nằm ngoài pipeline.

Luồng triển khai:

```text
pull request -> dependency review + full dependency scan + CI verify
             -> merge main -> CI main xanh
             -> build image sha-<commit> -> runtime image scan
             -> Docker Hub -> resolve digest
             -> GitHub OIDC -> AWS SSM -> /opt/shop/deploy.sh
             -> health check -> giữ bản mới hoặc quay lại image trước
```

CD bị khóa mặc định. Thiếu biến hoặc secret không làm pipeline tự ý truy cập máy chủ.

SHA tag dùng để truy vết commit; deployment thực tế dùng digest `docker.io/t1r4m15u/shop@sha256:...` do registry trả về. Container ứng dụng chạy read-only, không có Linux capability và bật `no-new-privileges`; log Docker được xoay vòng để tránh lấp đầy ổ đĩa.

Pull request bị chặn nếu GitHub Dependency Review phát hiện dependency mới từ mức Moderate, hoặc Trivy phát hiện lỗ hổng đã có bản vá từ mức Medium trong toàn bộ dependency Maven. Trước khi push lên Docker Hub, CD còn quét riêng package hệ điều hành của runtime image và chặn High/Critical; image chưa vượt cổng này không được xuất bản.

## 1. Chuẩn bị Ubuntu một lần

Cài Docker Engine, Docker Compose plugin, `curl` và bảo đảm EC2 là Systems Manager managed node. Instance role cần quyền tối thiểu của Systems Manager; không cần mở SSH cho GitHub Actions.

Sao chép bundle từ repository vào máy chủ:

```bash
sudo install -d -m 0750 /opt/shop/mysql/init
sudo install -m 0750 deploy/deploy.sh /opt/shop/deploy.sh
sudo install -m 0640 deploy/compose.prod.yml /opt/shop/compose.prod.yml
sudo install -m 0750 deploy/mysql/init/01-create-accounts.sh \
  /opt/shop/mysql/init/01-create-accounts.sh
sudo cp deploy/.env.prod.example /opt/shop/.env.prod
sudo chmod 0600 /opt/shop/.env.prod
sudo editor /opt/shop/.env.prod
```

Sinh từng secret độc lập, không dùng lại giữa MySQL, MinIO và JWT:

```bash
openssl rand -base64 48
openssl rand -base64 64
```

`MYSQL_BIND_ADDRESS`, `MINIO_BIND_ADDRESS` và `APP_BIND_ADDRESS` mặc định là `127.0.0.1`. Nếu ALB gọi trực tiếp cổng ứng dụng, đổi riêng `APP_BIND_ADDRESS=0.0.0.0` và chỉ cho phép Security Group của ALB. Nếu cần quản trị MySQL từ xa, chỉ mở `3306` cho IP quản trị `/32`; không dùng `0.0.0.0/0` làm source.

Script MySQL init chỉ chạy khi volume database được tạo lần đầu. Với volume có sẵn, tạo hoặc chỉnh `shop_runtime` và `shop_migration` bằng tài khoản quản trị trước khi deploy. Runtime chỉ có DML; Flyway dùng tài khoản migration riêng.

Kết nối từ ứng dụng và Flyway tới MySQL bắt buộc TLS (`sslMode=REQUIRED`). MySQL image tạo chứng thư máy chủ khi khởi tạo volume; không hạ xuống `DISABLED` hoặc bật public-key retrieval chỉ để bỏ qua lỗi xác thực.

Nếu Docker Hub repository là private, đăng nhập một lần trên EC2 bằng access token chỉ có quyền đọc:

```bash
read -rsp 'Docker Hub read token: ' DOCKER_READ_TOKEN
printf '%s' "$DOCKER_READ_TOKEN" | sudo docker login docker.io \
  --username t1r4m15u --password-stdin
unset DOCKER_READ_TOKEN
```

## 2. Bootstrap deployment

Trước khi bật tự động, triển khai thủ công một image SHA đã tồn tại:

```bash
sudo /opt/shop/deploy.sh \
  docker.io/t1r4m15u/shop:sha-<40-ky-tu-commit>
```

Pipeline tự động sẽ truyền digest tương ứng thay vì tag. Script chỉ chấp nhận SHA tag đúng định dạng khi bootstrap thủ công hoặc digest `sha256` của đúng Docker Hub repository.

Kiểm tra:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
sudo docker compose --env-file /opt/shop/.env.prod \
  -f /opt/shop/compose.prod.yml ps
```

## 3. Cấu hình GitHub

Repository secret:

- `DOCKERHUB_TOKEN`: Docker Hub access token có quyền push repository `t1r4m15u/shop`.

Repository variables:

- `DOCKERHUB_USERNAME=t1r4m15u`
- `CD_ENABLED=true` chỉ đặt sau khi token Docker Hub đã sẵn sàng.
- `DEPLOY_ENABLED=true` chỉ đặt sau khi bootstrap EC2 và SSM thành công.
- `AWS_ACCOUNT_ID`
- `AWS_REGION`
- `AWS_DEPLOY_ROLE_ARN`
- `EC2_INSTANCE_ID`

Tạo GitHub environment `production`; nên yêu cầu approval trước khi job lấy OIDC token và chạy deployment.

Trust policy của AWS role phải giới hạn đúng repository và environment:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:triramisu/shop:environment:production"
        }
      }
    }
  ]
}
```

Role chỉ cần `ssm:SendCommand` tới EC2 đích và document `AWS-RunShellScript`, cùng `ssm:GetCommandInvocation`. Không cấp quyền quản trị AWS tổng quát.

## 4. Rollback và dữ liệu

`deploy.sh` nhớ image đang chạy trước khi thay container. Nếu health check bản mới không lên, script thử chạy lại image trước và vẫn trả exit code lỗi để GitHub hiển thị deployment thất bại.

Rollback image không hoàn tác Flyway migration. Mọi migration production phải forward-only và tương thích với ít nhất phiên bản ứng dụng trước hoặc có kế hoạch roll-forward riêng. Trước migration rủi ro, backup và diễn tập restore là bắt buộc.

Rollback thủ công:

```bash
sudo /opt/shop/deploy.sh \
  docker.io/t1r4m15u/shop:sha-<commit-truoc>
```

## 5. UI và ảnh sản phẩm

UI có pipeline S3 riêng và phải build với API base URL của backend. `CORS_ALLOWED_ORIGINS` trên EC2 phải là origin chính xác của UI.

Bucket UI không thay thế object storage ảnh sản phẩm. Backend hiện dùng MinIO; chuyển media sang Amazon S3 cần một adapter backend và IAM policy riêng, không dùng chung credential hoặc bucket UI.
