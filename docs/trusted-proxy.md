# PAY-SIT-01B — Trusted client IP trước nghiệm thu VNPay

## Hợp đồng — 24/10/2026

1. **Mục tiêu/phạm vi:** backend nhận IP viewer do CloudFront xác lập, không
   tin IP do client tự gửi. Áp dụng cho VNPay handoff và auth rate limit đang
   dùng `request.getRemoteAddr()`. Không tạo giao dịch/USER, bật refund/worker,
   auto-ban hoặc coi email đã gửi là VNPay đã đăng ký IPN.
2. **Phụ thuộc:** template live SHA-256
   `19ef36d981b748db7975ca52ae161af40571891a87fdd7070bf1b0f63e3c6d19`,
   57 resource, LT6, sandbox. Nhánh local xếp trên `7db9d34` vì baseline đã
   deploy chưa merge; origin/main đã fetch vẫn `847b093`. Không tự push/merge.
3. **Luồng/bất biến:** CloudFront xóa header IP do viewer gửi rồi ghi header
   riêng từ `event.viewer.ip` cho API. Native Tomcat chỉ đọc header đó khi
   socket peer thuộc hai subnet ALB. Giữ route, query/cookie/Authorization,
   HMAC callback, TLS, private origins và origin-verification secret.
4. **An toàn:** trust chỉ có ý nghĩa cùng SG app chỉ nhận ALB, SG ALB chỉ
   nhận CloudFront và listener kiểm origin secret. Không trust mọi private
   IP/X-Forwarded-For, không nới SG/IAM. Không để header host/port của viewer
   điều khiển URL; không log IP/header thô hoặc secret trong probe.
5. **Đầu ra:** thay Function/Launch Template và dependency triển khai;
   test Function bằng event synthetic, test binding/RemoteIpValve thật của
   Boot/Tomcat đang dùng; bootstrap/semantic-diff tests và runbook.
   Không thay Java production, image, AMI, schema V26 hoặc secret.
6. **Ca kiểm tra:** viewer IPv4/IPv6, header giả/lặp, route `/api`/`/api/*`,
   SPA/static, restricted/public; ALB peer được tin, peer ngoài phạm vi bị
   bỏ qua; không có header vẫn giữ peer; spoof XFF/host/port không đổi IP/URL.
7. **Gate:** syntax/Node, test Java cấu hình và auth/VNPay regression;
   cfn-lint, Guard so baseline, AWS preflight/change set. Không đánh dấu xong
   từ local tests: phải hậu kiểm hai instance/targets, flags và IP spoof sau
   rollout. Source >51.200 byte phải dùng `--template-url` qua artifact S3
   private/mã hóa đã kiểm ownership, không dùng UI/product bucket hoặc bỏ
   context bảo mật để né giới hạn; tối đa 1.048.576 byte qua S3.
8. **Rollout/rollback:** review plan cụ thể trước Execute, publish canonical
   edge header trước khi LT native được tạo, native instance refresh giữ
   2/2/4 và 100%/150%, có thể tạm 3 máy tính phí. Giữ payment sandbox/IPN
   trong rollback; quay về cấu hình proxy none trước khi gỡ edge header,
   không tự terminate/ép refresh/xóa RDS/S3. Chưa duyệt go-live.

## Trạng thái

Đã qua các gate chuẩn bị bên dưới; **chưa Execute hoặc thay runtime AWS** ở
nhiệm vụ này. Người dùng đã gửi email đăng ký IPN; chưa có bằng chứng VNPay
xác nhận. Không có USER/order/payment transaction mới được tạo từ nhiệm vụ.

## Cấu hình cụ thể và lý do

```text
Viewer → CloudFront Function (ghi đè x-shop-client-ip)
       → ALB HTTPS + origin secret + giới hạn SG
       → Tomcat native RemoteIpValve (chỉ tin socket peer ALB)
       → request.getRemoteAddr() → auth limit / VNPay handoff
```

Function xóa `x-shop-client-ip`, `Forwarded`, `X-Forwarded-Host` và
`X-Forwarded-Prefix` do viewer gửi. Với `/api` hoặc `/api/*`, Function gán
lại toàn bộ header `x-shop-client-ip` từ `event.viewer.ip`, kể cả khi đầu vào
có nhiều giá trị. Không thêm header IP cho S3. Không dùng phần IP đầu tiên
trong XFF; ALB có thể append vào XFF đã bị client tự chèn.

Launch Template chốt tám biến qua cờ Docker `-e`, sau env-file. Contract ở
`src/test/resources/contracts/trusted-proxy.env` được test Java và Node cùng
đọc. Tên env canonical bỏ dấu gạch nối trong tên property của Spring:

```dotenv
SERVER_FORWARDHEADERSSTRATEGY=native
SERVER_FORWARD_HEADERS_STRATEGY=native
SERVER_TOMCAT_REMOTEIP_REMOTEIPHEADER=x-shop-client-ip
SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES=^10[.]0[.](1|3)[.][0-9]{1,3}$
SERVER_TOMCAT_REMOTEIP_TRUSTEDPROXIES=^$
SERVER_TOMCAT_REMOTEIP_PROTOCOLHEADER=x-forwarded-proto
SERVER_TOMCAT_REMOTEIP_HOSTHEADER=
SERVER_TOMCAT_REMOTEIP_PORTHEADER=
```

Giữ alias forward-strategy hiện hành và tên canonical cùng `native`, tránh
hai tên trỏ giá trị khác nhau. Hai giá trị host/port rỗng là **override CLI**,
không thêm vào application secret: validator env-file hiện chặn key rỗng
ngoài danh sách VNPay optional. ALB cung cấp proto, không cho proxy header
host/port điều khiển URL. Peer ngoài `10.0.1.0/24` và `10.0.3.0/24` không
được tin; không trust loopback, Docker gateway hoặc các subnet app/DB.

LT phụ thuộc `ViewerRequestFunction`; AutoPublish của Function được giữ
nguyên để cấu hình edge cập nhật trước LT native. ASG vẫn dùng refresh do
CloudFormation sở hữu, health 100%/150%, warmup/bake 180 giây, grace 1.200
giây. Không thêm lệnh `start-instance-refresh` thủ công.

## Bằng chứng trước Execute — 24/10/2026

- Stack live UPDATE_COMPLETE; hai app LT6 InService/Healthy, hai target
  Healthy. Đường SG live: app chỉ 8080 từ ALB và SSH từ bastion; ALB chỉ
  HTTPS443 từ CloudFront origin-facing prefix list. Listener mặc định403;
  đúng một rule forward target, origin-verification value khớp CloudFront
  trong RAM. Không in value/header gốc ra output.
- Manifest/JAR live trên cả hai máy khớp Boot4.1.1/Tomcat11.0.27. SSM read-only
  `4c7c27e2-1fb9-4b46-b3cb-0af77ab06841` Success cả hai máy.
- Probe socket namespace container, chuẩn hóa IPv4-mapped IPv6, command
  `4a009620-5542-4eff-be06-7bee86a26055`: cả hai listen8080; 4 và 9 mẫu peer
  đều thuộc subnet ALB, không parse error. Có 5 Catalog GET nhỏ, đều200;
  không phải load test hoặc bắt packet payload. Lượt probe đầu chưa có socket
  được thay bằng probe lấy mẫu có traffic, không suy diễn từ kết quả rỗng.
- DNS tại thời điểm kiểm tra trả 4 địa chỉ, đối chiếu feed IP AWS chính thức
  đều thuộc CLOUDFRONT. UI200 có header CloudFront, không CF-Ray. Đây là
  bằng chứng điểm thời gian, không cam kết DNS không đổi sau này.
- Node94 test, không fail/skipped: preflight55, bootstrap/template24,
  trusted-proxy15. Test chạy FunctionCode thực bằng event synthetic và mock
  **toàn bộ** câu Docker để bắt lỗi continuation/quote, không gọi AWS.
- Java88 test/7 suite, không fail/error/skipped: auth rate-limit3,
  Identity architecture8, Modulith2, VNPay provider22, IPN35, Payment
  architecture6, native config12. Native config dùng Binder/Customizer và
  RemoteIpValve thật của phiên bản Boot/Tomcat hiện hành, kiểm peer tin/không
  tin, IPv6, thiếu header, spoof XFF/host/port. Sau format chạy lại native12
  đạt. Test tích hợp ở đây dùng H2; không gọi đó là MySQL/full milestone gate.
- `spotless:apply` và `spotless:check` đạt; không đổi file ngoài phạm vi.
  `git diff --check` đạt. Lần check Spotless trước bắt format file test mới,
  đã sửa bằng formatter và chạy lại, không bỏ qua gate.
- cfn-lint1.57.2: không error/warning/info. Guard3.2.1 với registry commit
  `7f7340c26ae5d5e8874651dbffeb12e0e9f505b6`: 193 rule documents, đúng 34
  failed Rule ID cũ, exit19; **không phải compliance pass**. Tool còn cảnh
  báo deprecation kiểu so sánh trong bộ rule; không sửa rule để làm xanh.
- CloudTrail `describe-trails --include-shadow-trails`: 0 trail; chưa có
  durable CloudTrail, giảm khả năng lưu audit dài hạn. Không tự tạo thêm
  dịch vụ/quyền. Coverage toàn backend chưa được đo lại ở task cấu hình này,
  không được suy ra đã đạt mục tiêu branch >80%.

### Lệnh local có thể lặp lại

Chạy ở thư mục checkout chứa candidate; Java21 và toolchain hiện có. Các
biến đường dẫn tooling chỉ áp dụng process hiện tại, không cài toàn máy.

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.12.1'
.\mvnw.cmd --batch-mode -q -Dtest=TrustedProxyNativeConfigurationTests,AuthenticationRateLimitIntegrationTests,IdentityLayerArchitectureTests,ModularArchitectureTests,VnpayProviderTests,VnpayIpnIntegrationTests,PaymentLayerArchitectureTests test
.\mvnw.cmd --batch-mode spotless:check
$env:SHOP_YAML_MODULE='C:/Users/minhp/AppData/Local/Temp/shop-publication-tools-e8c98a0cb8e04ed68f0644798497fa3f/node_modules/js-yaml'
$env:SHOP_BASH='D:/git/Git/bin/bash.exe'
$env:SHOP_PYTHON='C:/Users/minhp/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
node --test --test-reporter=dot ci/aws-vnpay-preflight.test.cjs ci/aws-vnpay-template.test.cjs ci/aws-trusted-proxy.test.cjs
git diff --check
```

Các thư mục Temp ở trên là tool đã có trên máy này; dự án khác phải tìm
tool hợp lệ của chính máy, không giả định các thư mục luôn tồn tại. Không
download/install lại hoặc tắt kiểm tra nếu thiếu tool mà chưa báo người dùng.

## Artifact và change set đang chờ duyệt

Candidate `deploy/aws/shop-prod.yaml`:

- 52.108 byte; SHA-256
  `319e79f59c257c20c7d4fb2d12f70d82b9dedafe0e78e19c04e703d209d6e7b9`.
- Artifact bucket có sẵn `cf-templates-yw9mur8k4zka-ap-southeast-1`, đúng owner
  và region; Block Public Access bốn cờ=true, BucketOwnerEnforced, SSE AES256.
- Key `shop/prod/trusted-proxy/319e79f59c257c20c7d4fb2d12f70d82b9dedafe0e78e19c04e703d209d6e7b9.yaml`.
  PutObject và HeadObject checksum SHA256/length khớp file đã test. Không có
  secret plaintext trong artifact, không tạo bucket/IAM mới hoặc upload UI.
- Template vượt giới hạn inline51.200 byte, dùng HTTPS S3 TemplateURL dưới
  giới hạn1.048.576 byte. Artifact phải giữ private; không bật public hoặc
  xóa source khi plan/rollback còn tham chiếu. Có phí S3 storage/request nhỏ.

Change set `trusted-proxy-20261024T101541Z`, stack `shop-aydibro-prod`, Singapore:

```text
arn:aws:cloudformation:ap-southeast-1:233377400170:changeSet/trusted-proxy-20261024T101541Z/3eb5aefc-02b0-4ad2-8595-f19dad2ef0f3
```

Revision mới `trusted-proxy-sit-20261024-01`; các parameter khác dùng previous
value. Đã CREATE_COMPLETE/AVAILABLE; `describe-events` không có
VALIDATION_ERROR hoặc pagination chưa xử lý.

| Resource | Mặc định | Enhanced property view |
| --- | --- | --- |
| ApplicationAutoScalingGroup | Modify / Conditional | Modify / False |
| ApplicationLaunchTemplate | Modify / False | Modify / False |
| CpuTargetTrackingPolicy | Modify / Conditional (reference) | không có diff đã resolve |
| UiDistribution | Modify / False (FunctionARN reference) | không có diff đã resolve |
| ViewerRequestFunction | Modify / False | Modify / False |

Review phạm vi hợp nhất **5 Modify**, không Add/Remove; không kết luận chỉ3
thay đổi hoặc chắc chắn không replacement từ enhanced view. ASG/policy
Conditional do dependency LT/group; không phải yêu cầu terminate hiện tại.
AMI/image, sandbox switches, DB, secret, IAM, SG, S3, UI, DNS và WAF giữ nguyên.

## Quy trình rollout sau khi được duyệt

1. Kiểm lại đúng account/profile/region, live source vẫn19ef…, PaymentMode
   sandbox, revision cũ, LT6, AMI/image không đổi, secret version không đổi,
   hai target Healthy và không có refresh đang chạy. Kiểm hash/artifact và
   cả hai view plan; có khác biệt phải dừng review lại, không blind Execute.
2. Chỉ sau xác nhận plan cụ thể, Execute **một lần**, không retry mù nếu mất
   response. Dùng command một dòng để không lẫn backtick PowerShell với Bash:

   ```text
   aws cloudformation execute-change-set --stack-name shop-aydibro-prod --change-set-name trusted-proxy-20261024T101541Z --profile shop-prod --region ap-southeast-1 --no-cli-pager
   ```

3. Theo dõi CFN events, native instance-refresh, LT/AMI và targets bằng các
   lệnh read-only; không gõ start-instance-refresh, stop/start hoặc terminate
   instance. Stack UPDATE_COMPLETE chưa đủ nếu refresh/health chưa đạt.

   ```text
   aws cloudformation describe-stacks --stack-name shop-aydibro-prod --query "Stacks[0].StackStatus" --profile shop-prod --region ap-southeast-1 --no-cli-pager
   aws autoscaling describe-instance-refreshes --auto-scaling-group-name shop-aydibro-prod-ApplicationAutoScalingGroup-vGFBGIo2wDtZ --max-records 3 --query "InstanceRefreshes[].{Id:InstanceRefreshId,Status:Status,Percent:PercentageComplete,Reason:StatusReason}" --profile shop-prod --region ap-southeast-1 --no-cli-pager
   aws elbv2 describe-target-health --target-group-arn arn:aws:elasticloadbalancing:ap-southeast-1:233377400170:targetgroup/shop-a-Appli-HDHO5KCE8IDU/9bc51ef52208fc8b --query "TargetHealthDescriptions[].{Id:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason}" --profile shop-prod --region ap-southeast-1 --no-cli-pager
   ```

4. Bootstrap hiện import114 RDS CA bằng keytool, lần trước cần khoảng12–14
   phút mỗi máy và toàn rollout khoảng33 phút. Không sửa truststore/AMI để
   rút ngắn trong task này; chờ readiness với grace hiện hành. Có thể tạm
   chạy3 EC2 có phí; không chạy tải/DDoS trong rollout.
5. Hậu kiểm cả hai app/targets ở hai AZ cùng LT mới, cùng AMI/image, refresh
   Successful và container healthy/restart0. SSM chỉ in kết quả boolean của
   tám native-env flags và payment flags; **không dump docker inspect/env,
   CloudFront origin headers hoặc secret**. Giữ sandbox/initiation/consumer
   bật; refund/query/reconciliation/recovery/bootstrapADMIN tắt.
6. Kiểm Function LIVE với event synthetic/header giả; giữ API behavior,
   query/cookies/auth và origin boundary. Smoke ít request UI/Catalog200,
   anonymous private401 và IPN checksum giả97/query rỗng99/no-store. Không
   thêm endpoint public để soi IP. Config/env + test không thay bằng chứng
   handoff IP thực tế và giao dịch; chỉ SIT mới xác nhận luồng đầy đủ.
7. Ghi chính xác kết quả, thời gian và hạn chế vào README/runbook. Chỉ sau
   VNPay xác nhận IPN mới chuyển sang task SIT kiểm success/fail/duplicate;
   người dùng tự đăng nhập merchant/SIT và nhập dữ liệu thẻ sandbox riêng.

## Rollback / điều kiện dừng

Nếu target cũ mất health, refresh fail hoặc source/secret/plan lệch, ngừng
thao tác tiếp và kiểm CFN/refresh/bootstrap đã redaction; không ép terminate,
nới SG/trust CIDR hoặc tắt HMAC. Dùng rollback do CloudFormation quản lý;
không tự đổi MinHealthy/bỏ wait gate để làm xanh.

Nếu phải rollback chủ động sau deployment, tạo **plan riêng được review**:
trước hết đưa native flags về cấu hình proxy `none` baseline sandbox19ef…,
tăng revision và rollout tuần tự trong khi **vẫn giữ** Function ghi đè header.
Sau khi hai máy thực sự proxy-none/Healthy, mới cân nhắc gỡ edge header ở
plan tiếp. Thứ tự này tránh khoảng thời gian app tin header client giả.
Không rollback sang `PaymentMode=none`, hạ schema, đổi secret hoặc xóa dữ liệu;
VNPay IPN đang chờ phải còn endpoint. Không restore RDS chỉ vì lỗi cấu hình proxy.

## Nguồn thiết kế

- [AWS — true client IP header](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/example_cloudfront_functions_add_true_client_ip_header_section.html)
- [AWS — ALB forwarded headers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html)
- [Spring — server properties](https://docs.spring.io/spring-boot/appendix/application-properties/)

Native configuration phải kiểm với Boot 4.1.1/Tomcat 11.0.27 trong dependency
hiện tại, không sao chép package/default của Boot 3.x. IPN đăng ký dự kiến
`https://aydibro.click/api/payments/webhooks/vnpay`; chỉ dùng sandbox.
