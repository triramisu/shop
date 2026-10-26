# AWS — chọn cấu hình hạ tầng

[Mục lục triển khai](../README.md) · [Mục lục tài liệu](../../docs/README.md)

## Đọc trước khi chọn file

| File/thư mục | Dùng để làm gì? | Phạm vi |
| --- | --- | --- |
| [`shop-prod.yaml`](shop-prod.yaml) | Baseline CloudFormation prod-profile, RDS/S3 và app private trong ASG | Điểm bắt đầu cho mô hình AWS hiện tại; đọc [PROD.md](PROD.md) |
| [`shop-lab.yaml`](shop-lab.yaml) | Template lab AWS trước đây | Giữ cho lịch sử/test; không mặc định dùng để update stack prod |
| [`application.env.example`](application.env.example) | Mẫu env-file text cho application secret trên AWS | Điền kín theo môi trường, không phải JSON parameters |
| [`parameters.example.json`](parameters.example.json) | Parameters mẫu của template lab cũ | Có tên key/AMI khác prod; không truyền nguyên trạng vào prod |
| [`.gitignore`](.gitignore) | Quy tắc bỏ qua env/parameters local và key/certificate riêng | Không thay cho việc kiểm tra secret trước commit |
| `iam/` | [Trust policy](iam/backend-github-trust.json) và [permissions](iam/backend-github-permissions.json) của role CD backend | Dùng cùng [ASG CD](../../docs/asg-cd.md) |

Các file mẫu không chứa secret đang chạy. Trước update phải đối chiếu source,
Original deployed template, parameters và phạm vi change set của đúng stack.
Trạng thái AWS live cần được kiểm tra riêng trước khi áp dụng kế hoạch.

## AWSLAB-01 — Hạ tầng lab Shop tiết kiệm

Phần dưới lưu thiết kế và các biên bản lab có ngày. Những câu như “chưa deploy”
hoặc engine/AMI/stack ID ở một biên bản chỉ phản ánh thời điểm đó. Trạng thái
mới hơn xem ở [README dự án](../../README.md#2-trạng-thái-hiện-tại),
[hồ sơ prod](PROD.md) và [ASG CD](../../docs/asg-cd.md).

## S3-01 — Cập nhật kho ảnh AWS

Template hiện có `ProductImagesBucket` riêng với `UiBucket`, dùng S3 Standard,
private/SSE-S3/versioning/retain; EC2 role chỉ ListBucket và Get/Put/Delete
trong `catalog/products/*`. Không tạo MinIO trên AWS. Hợp đồng, test và cutover
xem [S3-01](../../docs/product-images-s3.md). Đây là thay đổi source local,
không xác nhận stack AWS đã cập nhật hoặc ảnh MinIO đã chuyển sang S3.

## AWSLAB-01R — Sửa lỗi RDS backup retention (19/10/2026)

1. **Mục tiêu/phạm vi:** sửa cấu hình lab sau khi chủ tài khoản tạo stack
   `shop-aydibro` và RDS từ chối giữ backup 3 ngày. Chỉ sửa source, test và
   runbook; không tự tạo lại/xóa stack, nâng account plan hoặc chạy payment.
2. **Sở hữu/dependency:** thuộc `deploy/aws`, chỉ đổi
   `Database.Properties.BackupRetentionPeriod`; không đổi API, credential,
   engine MySQL hoặc migration V25. Cần kiểm tra lại giới hạn của tài khoản AWS.
3. **Luồng/invariant:** dùng retention **1 ngày**, không dùng `0` (tắt backup
   tự động). Giữ `Snapshot` khi delete/replace và sao chép tag vào snapshot.
4. **Bảo mật/vận hành:** giữ RDS private, encrypted, TLS, secret quản lý bởi
   RDS và các security group. Cửa sổ phục hồi giảm từ 3 xuống 1 ngày; đây là
   đánh đổi cho lab, không phải khuyến nghị recovery production.
5. **Đầu ra:** YAML, test regression giữ retention/snapshot và tài liệu tiến độ.
6. **Case:** cấu hình 1 ngày được test chấp nhận; đổi về 3 hoặc 0 phải làm test
   lỗi; bỏ snapshot/private/encryption vẫn bị regression hiện có phát hiện.
7. **Gate:** `node --test ci/aws-template.test.cjs`, kiểm tra whitespace;
   ghi rõ test skipped nếu thiếu runtime. Local pass không chứng minh AWS đã
   nhận cấu hình hoặc toàn bộ stack tạo thành công. Lỗi trong ảnh không nêu
   giới hạn tối đa cụ thể; không suy ra mọi Free account đều có cùng giới hạn.
8. **Deploy/rollback:** xem trạng thái stack và tài nguyên giữ lại trước khi
   thử lại; không xóa tự động khi `ROLLBACK_COMPLETE`. Nếu cập nhật DB đã tồn
   tại, review change set và recovery trước khi giảm retention. Chỉ tăng lại
   3 ngày khi account plan cho phép; không tự nâng plan để gỡ lỗi. Một ngày
   backup không làm NAT/ALB/EC2/RDS và cả stack trở thành miễn phí.

## AWSLAB-01D — Thử lại foundation được ủy quyền (19/10/2026)

1. **Mục tiêu/phạm vi:** theo xác nhận của chủ tài khoản, chuẩn bị và tạo
   stack mới `shop-aydibro-v2` tại `ap-southeast-1`, chế độ `foundation`;
   giữ nguyên stack lỗi, hai bucket S3 và log group cũ. Không tự nâng plan,
   bật application mode, đổi DNS, xóa dữ liệu hoặc triển khai Payment.
2. **Dependency:** dùng YAML AWSLAB-01R đã kiểm thử, parameters đối chiếu
   Console của stack cũ, key pair hiện có và certificate đã Issued cho
   `shop.aydibro.click`. Không sửa schema hoặc credential; CLI đăng nhập
   tài khoản khác không được dùng để tạo resource cho phiên Console này.
3. **Luồng/invariant:** review wizard trước khi submit, foundation chỉ cài
   Docker/AWS CLI trên host, không chạy Shop; RDS giữ backup 1 ngày và
   snapshot delete/replace. Không tạo thêm một stack khi lần thử mới còn chạy.
4. **Bảo mật/vận hành:** giữ SSH /32, DB private/TLS/encrypted, IAM theo
   template. Tạo mới IAM/network access cần xác nhận tại bước áp dụng;
   NAT/ALB/EC2/RDS và tài nguyên giữ lại vẫn có thể tính phí.
5. **Đầu ra:** một lần thử foundation mới, trạng thái/events/outputs quan
   sát được, screenshot và cập nhật tiến độ; không push/merge Git.
6. **Case:** certificate/domain/region khớp; thiếu file, account sai hoặc
   validation thất bại phải dừng. Nếu AWS tiếp tục từ chối cấu hình, đọc lỗi
   gốc và báo lại, không tắt backup/encryption hay tự chuyển sang Paid plan.
7. **Gate:** chạy lại 21/21 Node checks không skipped, kiểm tra source hash,
   wizard validation và events. Chỉ `CREATE_COMPLETE` mới xác nhận foundation
   tạo xong; chưa là nghiệm thu bootstrap thực tế, API/UI hoặc mua hàng.
8. **Rollback:** bật rollback khi create thất bại, giữ deletion policies;
   kiểm kê tài nguyên retain sau lỗi. Không tự xóa stack/bucket/snapshot,
   không retry tạo thêm stack hoặc thay đổi account để vượt giới hạn.

## AWSLAB-01C — Thu hồi riêng foundation v2 lỗi (19/10/2026)

1. **Mục tiêu/phạm vi:** theo xác nhận tiếp tục của chủ tài khoản sau khi
   xem phương án xóa, thu hồi riêng `shop-aydibro-v2` tại Singapore. Giữ
   stack `shop-aydibro` cũ, S3/log và mọi snapshot; không tạo stack mới.
2. **Dependency:** kiểm tra đúng account/region/stack ARN trong Console,
   RDS `Available`, template thật giữ `Database: Snapshot`, hai bucket và
   application log group `Retain`. Không dùng CLI đang ở account khác.
3. **Luồng/invariant:** xóa bằng CloudFormation thông thường, không Express
   hoặc force; để CloudFormation tạo final snapshot trước khi xóa DB và
   thu hồi các dependency mạng của v2. Không bỏ policy để ép hoàn tất.
4. **Bảo mật/vận hành:** không đọc credential hoặc dữ liệu nghiệp vụ,
   không mở mạng/cấp quyền mới. DB endpoint dừng; snapshot/retained
   resources có thể tiếp tục tính phí. Đây không phải kiểm kê toàn account.
5. **Đầu ra:** trạng thái stack/events, final manual snapshot, danh sách
   resource retain, screenshot và cập nhật tiến độ; không đổi YAML/API/schema.
6. **Case:** đúng target và policy mới xóa; lỗi snapshot hoặc xóa phải
   dừng để đọc nguyên nhân, không tự chuyển sang skip/force/delete dữ liệu.
   Stack cũ và resource retain không được đưa vào phạm vi cleanup.
7. **Gate:** quan sát `DELETE_COMPLETE`, RDS instance không còn, final
   snapshot `Available`/encrypted đúng DB nguồn, hai bucket/log
   `DELETE_SKIPPED`; kiểm tra tài liệu bằng `git diff --check`.
8. **Khôi phục:** không thể undo xóa stack. Phục hồi cần tạo DB mới từ
   snapshot, cấu hình credential/network và nghiệm thu restore riêng;
   không coi snapshot `Available` là bằng chứng restore đã chạy thành công.

## AWSLAB-01P — Xóa resource retain theo xác nhận riêng (19/10/2026)

1. **Mục tiêu/phạm vi:** chủ tài khoản chọn tự dựng hạ tầng, xác nhận xóa
   vĩnh viễn phần còn lại của hai stack `shop-aydibro` và `shop-aydibro-v2`
   đã liệt kê: bốn bucket, hai log group, các bản sao DB v2 và stack cũ.
   Không coi yêu cầu này là quyền xóa toàn account hoặc các region khác.
2. **Dependency:** đối chiếu resource physical ID từ hai stack với S3,
   CloudWatch và RDS Console ở đúng account/Singapore. V25/API/code và
   YAML local không thay đổi; CLI account khác không được dùng thao tác AWS.
3. **Luồng/invariant:** chỉ xóa đúng target đã kiểm kê/duyệt; đọc lại hộp
   xác nhận mỗi resource, theo dõi kết quả UI. Không chọn bucket theo một
   wildcard rộng hoặc xóa IAM/key/certificate/domain để dọn cho nhanh.
4. **Bảo mật/vận hành:** chủ tài khoản đã xác nhận tại bước xóa dữ liệu,
   gồm snapshot cuối và mọi object/version trong bốn bucket nếu có.
   Không đọc credential, file nghiệp vụ hoặc thay đổi quyền/bảo mật.
5. **Đầu ra:** resource trong phạm vi được xóa, screenshot và tiến độ đúng
   trạng thái; giữ tài liệu/YAML để tham khảo, không tự tạo lại stack.
6. **Case:** sai account/target hoặc thấy dependency ngoài phạm vi thì
   dừng phần đó; resource đã mất không xóa lặp; không báo xong chỉ từ thao
   tác bấm nút. Bucket template dùng chung cần duyệt riêng nếu muốn xóa.
7. **Gate:** hai stack `DELETE_COMPLETE`, bốn bucket không còn trong S3,
   hai log group bị xóa, manual/system snapshots và retained backups không
   còn cho DB v2; `git diff --check` và kiểm tra whitespace tài liệu.
8. **Rollback:** không có undo cho snapshot/bucket/log đã xóa. Không còn
   phục hồi từ snapshot vừa xóa; tạo lại hạ tầng phải dùng cấu hình/dữ liệu
   mới hoặc backup độc lập do chủ tài khoản quản lý. Không khẳng định hóa
   đơn toàn account về 0 từ cleanup hai stack.

## AWSLAB-01T — Xóa hai bucket upload template theo duyệt riêng (19/10/2026)

1. **Mục tiêu/phạm vi:** chủ tài khoản xác nhận riêng xóa
   `cf-templates-yw9mur8k4zka-ap-southeast-1` và
   `cf-templates-yw9mur8k4zka-ap-southeast-2`, toàn bộ dữ liệu/version.
   Chỉ hai bucket này; không xóa bucket `d-s3-shop-demo-sk` hoặc tài nguyên khác.
2. **Dependency:** đúng account Console đã kiểm kê, đối chiếu tên/region
   Singapore và Sydney. Không dùng CLI account khác; không sửa API/V25/YAML.
3. **Luồng/invariant:** Empty từng bucket, đợi success và không có object
   deletion failed, rồi Delete chính bucket đó. Không rời trang Empty khi
   còn chạy, không xóa bằng wildcard hoặc thao tác nhầm bucket tạo tay.
4. **Bảo mật/vận hành:** xác nhận tại bước xóa vĩnh viễn đã bao gồm nội dung
   có thể phục vụ stack khác. Không đổi bucket policy, IAM, versioning hay
   Object Lock để né lỗi; nếu bị từ chối phải đọc nguyên nhân và báo lại.
5. **Đầu ra:** hai bucket không còn, screenshot Empty/Delete và cập nhật
   tiến độ; template local và source Git vẫn được giữ làm tài liệu tham khảo.
6. **Case:** resource đã mất không xóa lặp; sai tên/account/region hoặc
   Empty còn lỗi phải dừng phần đó, không báo đạt từ thao tác bấm nút.
7. **Gate:** success Empty/Delete của cả hai bucket; danh sách S3 không
   còn hai tên, bucket tạo tay vẫn có; kiểm tra diff/whitespace tài liệu.
8. **Rollback:** không undo object/version hoặc bucket đã xóa. Local YAML
   không thay thế mọi file đã từng upload; tạo lại hạ tầng thủ công phải
   dùng cấu hình được chủ tài khoản kiểm tra, không tự triển khai CloudFormation.

## Hợp đồng nhiệm vụ (trước triển khai)

1. **Mục tiêu/actor/phạm vi:** chủ tài khoản tạo một CloudFormation stack cho
   lab riêng; VPC hai AZ, bastion, ALB, ASG, RDS, S3/CloudFront. Không tạo tài
   nguyên AWS trong lượt làm việc này, không chạy tấn công, không đóng Payment.
2. **Sở hữu/dependency:** cấu hình thuộc `deploy/aws`; backend vẫn là modular
   monolith. Cần AWS region có hai AZ, key pair, ACM certificate cùng region với
   ALB, DNS origin, Docker image thật và secret ngoài Git khi bật ứng dụng.
   Không migration nghiệp vụ mới; MySQL 8.4 phải kiểm chứng riêng vì Shop hiện
   mới có bằng chứng trên 8.0.46.
3. **Luồng/invariant:** frontend đi CloudFront → S3 REST private; `/api` và
   `/api/*` đi CloudFront → ALB → EC2 private → RDS isolated. Một NAT dùng chung;
   ASG 2–4 máy, CPU target 60%. Không cache API hoặc chuyển lỗi API thành HTML.
4. **Bảo mật/vận hành:** SSH chỉ IP quản trị /32, app chỉ nhận ALB, DB chỉ nhận
   app/bastion. TLS viewer và origin; IMDSv2, EBS/RDS mã hóa, S3 chặn public;
   master secret không cấp cho app. Giới hạn máy không phải giới hạn hóa đơn.
5. **Đầu ra:** YAML, ví dụ parameters không chứa credential, runbook, kiểm tra
   cấu trúc và invariant bằng Node. Chỉ sửa tài liệu tiến độ liên quan.
6. **Case nghiệm thu:** foundation không giả backend healthy; app mode thiếu
   image/secret bị từ chối; IP ngoài lab bị chặn ở edge; không mở SSH/DB toàn
   Internet; SPA rewrite không áp dụng API; rollback không xóa dữ liệu RDS/S3.
7. **Bằng chứng:** parse YAML, kiểm tra resource graph/invariant, chạy thử
   CloudFront Function bằng fixture, kiểm tra shell và `git diff --check`.
   Kiểm tra local không thay thế CloudFormation change set và smoke test AWS.
8. **Deploy/rollback:** foundation trước, application sau qua Launch Template
   và rolling update; không SSH cài app thủ công trên từng node. RDS snapshot,
   S3 retain; khôi phục image digest/secret version có chủ đích. Không tự xóa
   stack, bucket, snapshot hoặc dữ liệu người dùng.

## AWSLAB-01U — Đổi EC2 host sang Ubuntu (hợp đồng trước khi sửa)

1. **Mục tiêu/phạm vi:** đổi bastion và ASG host sang Ubuntu Server 24.04 LTS
   amd64; không đổi base image container backend, không tạo/cập nhật stack AWS.
2. **Dependency:** AMI public của Canonical qua SSM; root device theo metadata
   AMI; Docker từ Ubuntu APT, AWS CLI từ snap chính thức AWS, OpenJDK 21 để tạo
   truststore trên host. Không đổi module, API hoặc migration V25.
3. **Luồng/invariant:** foundation vẫn chưa chạy Shop; application vẫn dùng
   image digest/secret chung, health signal và rolling update. Network/ASG/RDS
   bounds không thay đổi.
4. **Bảo mật/vận hành:** giữ IMDSv2, mã hóa EBS, SSH /32, outbound 443. Đổi APT
   mirror về HTTPS; không thêm user `ubuntu` vào Docker group mặc định. Không
   đưa secret hoặc access key AWS vào userdata.
5. **Đầu ra:** sửa YAML, README, test regression Ubuntu và hướng dẫn Termius;
   liệt kê rõ resource đã cấu hình và các việc chưa triển khai.
6. **Case:** sai OS/version bị từ chối; AWS CLI sẵn sàng trước signal; loại bỏ
   `dnf`/Corretto/`ec2-user`; hai root mapping là `/dev/sda1`; giữ no-cache API.
7. **Gate:** kiểm tra AMI/owner/device read-only, Node checks, Bash syntax và
   bootstrap mock, AWS resource spec, `validate-template`, format/diff check.
   Không coi mock hoặc validate-template là chạy thử EC2 thật.
8. **Deploy/rollback:** thay AMI có thể replace bastion và rolling app nodes;
   snapshot/retain DB/S3 không thay đổi. Stack cũ phải cập nhật parameter
   `LatestAmiId` có chủ đích; chỉ đổi Default trong YAML không thay giá trị
   parameter đã lưu. Nghiệm thu boot thật còn phải làm sau khi được deploy.

## Phương án đã chọn

```text
IP quản trị / IP lab được cho phép
                 │ HTTPS
             CloudFront
              ├─ UI ───────→ S3 private (OAC)
              └─ /api/* ───→ ALB HTTPS (2 public subnet)
                                  │ 8080, chỉ trong VPC
                           ASG: 2 → tối đa 4 EC2
                          app-private-a / app-private-b
                                  │ MySQL TLS
                         RDS MySQL Single-AZ
                          db-isolated-a / db-isolated-b

Termius → bastion public → SSH tới EC2 private
App private → 1 NAT Gateway ở public-a → Internet outbound
App private → S3 Gateway Endpoint → S3 cùng region
```

S3 Gateway Endpoint **không phải URL mở website**, không có tác dụng làm
CloudFront/S3 tự chạy React. Vite build thành file tĩnh; CloudFront phân phối
`dist/` từ S3 REST origin private. Không bật S3 website hosting hoặc public
bucket. Tham khảo [S3 Gateway Endpoint](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html)
và [CloudFront OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html).

| Thành phần  | Mặc định lab                                                       |
| ----------- | ------------------------------------------------------------------ |
| VPC         | `10.0.0.0/16`, hai Availability Zone                               |
| Public      | A `10.0.1.0/24`, B `10.0.3.0/24`                                   |
| App private | A `10.0.2.0/24`, B `10.0.4.0/24`                                   |
| DB isolated | A `10.0.5.0/24`, B `10.0.6.0/24`, không default route              |
| NAT         | Một NAT tại AZ A, cả hai app subnet dùng chung                     |
| Bastion     | `t3.micro`, chỉ SSH từ `AdminCidr /32`                             |
| App         | Ubuntu Server 24.04 LTS amd64, `t3.small`, Docker Java 21 non-root |
| ASG         | min/desired 2, max 4; warmup 300s, health grace 600s               |
| Scale       | Target tracking CPU trung bình 60%, EC2 detailed monitoring        |
| RDS         | MySQL 8.4.11, `db.t4g.micro`, Single-AZ, gp3 20 GiB                |
| UI          | S3 private, versioning; CloudFront HTTPS, API không cache          |

AWS không có một quy tắc đặt tên bắt buộc chung cho mọi dịch vụ. Template dùng
đúng resource type `AWS::*`, logical ID rõ ràng và tag `shop-lab-*`. Với resource
có giới hạn tên hoặc cần tên duy nhất toàn cầu, CloudFormation tự sinh tên;
không hard-code bucket/ALB/ASG name gây xung đột stack.

### Hai giai đoạn trong cùng một template

- **`foundation` mặc định:** tạo mạng và tài nguyên; hai app EC2 cài AWS CLI/Docker,
  phát tín hiệu bootstrap thành công. Không có Apache/health giả. Target group
  sẽ unhealthy và `/api` chưa dùng được (thường 503); ASG dùng EC2 health check,
  **chưa bật CPU scaling policy**, tránh thay máy liên tục khi chưa có backend.
- **`application`:** yêu cầu image digest và application secret đã chuẩn bị.
  Mỗi EC2 tự lấy cùng cấu hình/image, tạo truststore RDS, chạy Shop và đợi health
  UP trước khi signal. ASG đổi sang ELB health check và bật CPU target tracking.
  Thay đổi Launch Template đi qua rolling update, một máy mỗi đợt; không cài
  app thủ công vào từng node bằng SSH vì máy mới sẽ không có bản cài đó.

CPU target tracking là cơ chế duy trì mức sử dụng trung bình, **không phải** cứ
CPU vượt 60% một lần thì lập tức thêm đúng một EC2. Có độ trễ metric, alarm,
boot image và JVM warmup. Tham khảo [AWS target tracking](https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-scaling-target-tracking.html).

### Hệ điều hành Ubuntu và các phần đã đổi

- **Cả bastion và ASG:** Ubuntu Server **24.04 LTS amd64** chính chủ Canonical,
  lấy AMI theo region qua public SSM parameter
  `/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id`.
  Dùng bản server thường, không Ubuntu Pro/Marketplace và không bản minimal.
- SSH user là **`ubuntu`**; key pair, jump host và giới hạn SSH /32 giữ nguyên.
  Root device trong EC2 mapping đổi sang **`/dev/sda1`**, vẫn mã hóa gp3,
  bastion 8 GiB và app 20 GiB. Trên Nitro, tên nhìn trong guest có thể là NVMe;
  không đổi mapping CloudFormation thành tên NVMe tự đoán.
- Script từ chối sai OS/version. Hai host chuyển APT mirror mặc định sang
  `https://archive.ubuntu.com/ubuntu/` và
  `https://security.ubuntu.com/ubuntu/`, giữ keyring kiểm chữ ký Ubuntu.
  Do đó **không cần mở outbound 80**; package Ubuntu đi qua NAT, không dựa vào
  S3 Gateway Endpoint như một kho APT.
- App bootstrap dùng `apt-get`, có retry download/timeout khóa dpkg, cài
  `docker.io`, CA certificates, curl và Python 3. App mode mới cài
  `openjdk-21-jre-headless`/OpenSSL để tạo RDS truststore trên host.
  Không thêm user `ubuntu` vào Docker group; lệnh quản trị Docker dùng `sudo`.
- AWS CLI v2 được cài qua **snap chính thức AWS** khi chưa có v2, trước khi
  đọc secret/phát signal CloudFormation. PATH có `/snap/bin`; không giả định
  Ubuntu đã cài sẵn AWS CLI hoặc APT có package `awscli` tương thích.
  SSM Agent dùng snap có sẵn trên AMI Ubuntu; không cài thêm bản deb trùng lặp.
- **Chỉ OS của EC2 thay đổi.** Dockerfile backend vẫn dùng Java 21 Temurin
  trên Alpine như trước; container không bắt buộc cùng distro với EC2 host.
  RDS là dịch vụ quản lý của AWS, không cài MySQL lên Ubuntu app node.

Nguồn đối chiếu: [Canonical CloudFormation/SSM AMI](https://documentation.ubuntu.com/aws/aws-how-to/instances/build-cloudformation-templates/),
[AWS CLI snap chính thức](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html),
[SSM Agent trên Ubuntu](https://docs.aws.amazon.com/systems-manager/latest/userguide/agent-install-ubuntu-64-snap.html).
Snap/AMI current/package updates chưa được pin thành golden AMI; ghi nhận AMI
và package version khi benchmark để kết quả có thể đối chiếu.

### Danh sách cấu hình có trong YAML

| Nhóm              | Đã cấu hình                                                                                                       |
| ----------------- | ----------------------------------------------------------------------------------------------------------------- |
| VPC/subnet        | VPC `10.0.0.0/16`; 2 public, 2 app-private, 2 DB-isolated ở 2 AZ; DNS support/hostnames                           |
| Routing           | 1 IGW, 1 NAT/EIP tại AZ A; app A/B ra ngoài qua NAT; DB không default route                                       |
| S3 endpoint       | Gateway Endpoint gắn app/public route tables; không thay CloudFront frontend                                      |
| Bastion           | Ubuntu 24.04 amd64, `t3.micro`, public IP, key pair, SSH user `ubuntu`, chỉ admin `/32`                           |
| App EC2           | Ubuntu 24.04 amd64, `t3.small` mặc định, không public IP, Docker qua APT, AWS CLI snap                            |
| ASG               | Min/desired 2, max 4; CPU target 60% khi app mode, detailed monitoring, warmup 300s                               |
| Update/health     | Launch Template version, rolling từng máy, bootstrap signals, `/actuator/health`, ELB health ở app mode           |
| ALB               | Internet-facing ở 2 public subnet, HTTPS 443 với ACM ARN người dùng cung cấp, target port 8080                    |
| Origin protection | SG chỉ nhận CloudFront origin prefix list + secret header; listener mặc định 403                                  |
| Security groups   | App 8080 chỉ ALB, SSH app chỉ bastion; RDS 3306 chỉ app/bastion; không public SSH/DB/app                          |
| Host/EBS          | IMDSv2 bắt buộc, gp3 encrypted, root `/dev/sda1`, bastion 8 GiB/app 20 GiB, T3 standard credits                   |
| RDS               | MySQL 8.4.11 Single-AZ `db.t4g.micro`, gp3 20 GiB fixed, private/encrypted, TLS required                          |
| DB credentials    | Master secret do RDS quản lý; app secret riêng, runtime/migration user tách biệt phải tạo trước                   |
| S3 ảnh sản phẩm   | Bucket riêng private/versioning/retain, IAM role giới hạn prefix; URL GET ký có TTL, S3 Gateway Endpoint dùng chung |
| RDS recovery      | Backup 1 ngày cho lab, snapshot khi delete/replace; không tự nâng major hoặc đăng ký Extended Support             |
| S3 UI             | Private, OAC, chặn public/HTTP, versioning, SSE-S3, retain; noncurrent versions hết hạn sau 7 ngày                |
| CloudFront        | HTTPS, IPv4 allowlist admin/lab, SPA rewrite ngoài API; `/api` và `/api/*` không cache, forward auth/query/cookie |
| IAM/SSM           | Instance roles cho SSM; app chỉ đọc application secret được chỉ định, ghi app log và signal stack                 |
| Container         | Profile prod, non-root theo Dockerfile, read-only, cap-drop, no-new-privileges, RAM 1536 MiB/CPU 1.5              |
| Payment lab       | Provider none, tắt refund/auto payment; không tạo charge thật                                                     |
| Logs/alarms       | App CloudWatch log 3 ngày/non-blocking, alarm unhealthy target và RDS free storage; chưa gửi email                |

**Chưa tự động hóa:** tạo/xác thực ACM, DNS Route53/Cloudflare, upload React
`dist/`, tạo DB users/application secret, di chuyển và nghiệm thu ảnh S3 thật,
CI/CD, WAF/Shield Advanced, budget/email alert và nghiệm thu multi-instance.
Hai chế độ foundation/application không tự hoàn thành các prerequisite này.

## Giới hạn và quyết định an toàn

1. Đây **không phải HA production**: một NAT là điểm lỗi chung; lưu lượng AZ B
   qua NAT AZ A có thể tính phí cross-AZ; RDS Single-AZ không có standby failover;
   bastion cũng chỉ có một máy. Hai app AZ không xóa các điểm lỗi này.
2. `MaxAppInstances=4` chỉ giới hạn ASG. Nó **không giới hạn tổng tiền** NAT,
   ALB, public IPv4, CloudFront/S3 request/egress, RDS, Secrets Manager hoặc log.
   Tạo AWS Budget + billing alert trước khi execute stack; alert không phải
   công tắc cắt tiền. Không coi cấu hình này là Free Tier.
3. T3 dùng **standard credits** để tránh phí Unlimited; hết credit có thể bị
   throttle, làm sai lệch kết quả benchmark. Không suy ra capacity production
   từ lab này. Docker giới hạn 1.5 CPU, 1536 MiB RAM, không swap thêm.
4. CloudFront Function chặn toàn bộ web ngoài `AdminCidr` và một IPv4 lab tùy
   chọn. IPv6 tắt để không bypass chính sách IPv4. Không tin X-Forwarded-For do
   client gửi. ALB chỉ nhận prefix list CloudFront và secret origin header;
   không mở 443 Internet để bỏ qua edge. Đây **không phải WAF rate limiter**;
   WAF rule set/rate-based rules chưa được triển khai trong task này.
5. Không bật Cloudflare proxy (orange cloud) trước CloudFront trong lab này:
   CloudFront sẽ thấy IP Cloudflare thay vì IP đã cho phép. Dùng **DNS-only**.
   Không mở SSH, 3306 hoặc 8080 cho `0.0.0.0/0`.
6. Viewer → CloudFront → ALB dùng HTTPS; ALB → app HTTP trong private VPC. Nếu
   yêu cầu mã hóa cả hop nội bộ, phải bổ sung HTTPS target/certificate trước khi
   gọi đây là end-to-end TLS.
7. App mode dùng **S3 ảnh sản phẩm riêng** qua IAM role, không cần MinIO
   credentials; local/dev vẫn dùng MinIO. Image phải có S3-01. Nếu metadata
   hiện có, phải copy/đối chiếu object key và bytes trước khi chuyển storage;
   không tự chuyển hoặc xóa ảnh cũ. Gateway endpoint chỉ là đường mạng EC2,
   không tự cấp quyền S3; browser GET URL ký đi ngoài VPC. Vì vậy bucket policy
   không ép mọi GET phải qua `aws:SourceVpce`. Không mở bucket public để chữa 403.
8. Lab ép provider `none`, tắt refund và auto-initiate payment; không đổi
   payment state bằng tay, không chạy giao dịch thật. Webhook provider không
   vào được allowlist lab hiện tại; tích hợp VNPay/Stripe cần thiết kế ingress
   webhook riêng. Đây không phải môi trường nghiệm thu Payment.
9. Rate limit một JVM chưa là rate limit phân tán; template không thêm Redis,
   không sửa trusted-proxy logic và không bật sticky sessions để che vấn đề.
   Nghiệm thu multi-instance cho login/CAPTCHA/checkout/idempotency là bước
   bắt buộc trước khi mở public. Không tạo dữ liệu/thanh toán thật khi đo tải.
10. App logs giữ 3 ngày, non-blocking (có thể mất log khi buffer đầy); không thay
    thế audit append-only. Chưa có ALB/CloudFront access log, WAF, CloudTrail
    data events, SNS notification hay session recording riêng của lab. Alarm
    chỉ hiện trong CloudWatch, không tự gửi email. Đây không phải chứng nhận
    đã harden đầy đủ cho production.

## 1. Việc bạn cần tự chuẩn bị

Các lệnh AWS dưới đây dùng **Bash** (CloudShell hoặc máy có AWS CLI đã đăng
nhập), không paste nguyên block vào PowerShell. Không gửi access key, mật
khẩu, JWT secret, private key SSH hoặc file env thật trong chat/Git.

```bash
export AWS_REGION=ap-southeast-1
aws sts get-caller-identity
aws ssm get-parameter --region "$AWS_REGION" \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value --output text
aws ec2 describe-availability-zones --region "$AWS_REGION" \
  --filters Name=state,Values=available --query 'AvailabilityZones[].ZoneName'
aws ec2 describe-managed-prefix-lists --region "$AWS_REGION" \
  --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
  --query 'PrefixLists[0].PrefixListId' --output text
aws rds describe-db-engine-versions --region "$AWS_REGION" \
  --engine mysql --engine-version 8.4.11 \
  --query 'DBEngineVersions[].{Version:EngineVersion,Family:DBParameterGroupFamily,Status:Status}'
aws rds describe-orderable-db-instance-options --region "$AWS_REGION" \
  --engine mysql --engine-version 8.4.11 --db-instance-class db.t4g.micro \
  --query 'OrderableDBInstanceOptions[].{Class:DBInstanceClass,Storage:StorageType}'
```

- Chọn region có ít nhất hai AZ; kiểm tra quota EC2, EIP, NAT, RDS, ALB,
  CloudFront. Prefix list CloudFront có trọng số lớn (55 SG rules); cần quota
  security group phù hợp. Không thay bằng CIDR Internet cho nhanh.
- Tạo/chọn **EC2 key pair** trong cùng region; giữ `.pem` trên máy cá nhân.
- Xin ACM certificate **cùng region với ALB** cho `origin.aydibro.click` và
  hoàn thành DNS validation tại nhà cung cấp DNS; certificate phải `ISSUED`.
  ACM cho origin ALB khác với certificate `us-east-1` của custom domain
  CloudFront. Template hiện dùng URL `*.cloudfront.net` cho UI, chưa gắn domain
  UI/Route53/Cloudflare tự động.
- Xác định public IPv4 hiện tại của bạn và máy lab. `AdminCidr` phải `/32`;
  `LabClientIpv4` là IP đơn, không `/32`. Đổi mạng/VPN cần cập nhật parameter.
- Có ngân sách/alert, quyền AWS tạo resource và `CAPABILITY_IAM`.

RDS 8.0 đã hết standard support trên AWS từ 31/07/2026; template chọn 8.4 và
tắt tự ghi danh Extended Support. **Không đồng nghĩa Shop đã kiểm thử MySQL
8.4**. Trước app mode: chạy Flyway fresh/upgrade trên bản sao, full MySQL
integration tests và purchase-flow smoke test; không nâng DB local thật chỉ
để tạo stack. Xem [lịch hỗ trợ RDS MySQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.Concepts.VersionMgmt.html).

## 2. Tạo foundation, chưa chạy backend

Trong AWS Console: CloudFormation → Create stack → Upload template file →
chọn `shop-lab.yaml` → điền parameters → review chi phí/quyền IAM → Create.
Để `DeploymentMode=foundation`. File JSON ví dụ không upload thay YAML.

Hoặc dùng CLI từ root repository. Sao chép `parameters.example.json` thành
`parameters.local.json` rồi thay **toàn bộ** placeholder; IP ví dụ `203.0.113.10`
không phải IP thật của bạn. File local đã được gitignore, vẫn không đặt secret
plaintext vào parameters vì CloudFormation có thể lưu/hiển thị chúng.

Nếu cập nhật stack đã từng tạo bằng Amazon Linux: **đặt rõ `LatestAmiId` thành
SSM path Ubuntu** trong parameters, không dùng `UsePreviousValue=true` cho
parameter này. Chỉ đổi `Default` trong template không thay parameter đã lưu.
Review replacement của bastion, nhận lại public IP/SSH host key sau khi xác
minh instance ID mới và rolling update app; không tắt kiểm tra host key SSH.
Không có thao tác chuyển đổi EC2 đang chạy trực tiếp trong nhiệm vụ sửa file.

```bash
aws cloudformation validate-template --region "$AWS_REGION" \
  --template-body file://deploy/aws/shop-lab.yaml
aws cloudformation create-change-set --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name foundation-v1 --change-set-type CREATE \
  --template-body file://deploy/aws/shop-lab.yaml \
  --parameters file://deploy/aws/parameters.local.json \
  --capabilities CAPABILITY_IAM \
  --tags Key=Project,Value=shop Key=Environment,Value=lab
aws cloudformation wait change-set-create-complete --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name foundation-v1
aws cloudformation describe-change-set --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name foundation-v1 \
  --query 'Changes[].ResourceChange.[Action,LogicalResourceId,ResourceType,Replacement]' --output table
```

**Chỉ sau khi bạn duyệt change set và chấp nhận chi phí**, mới chạy:

```bash
aws cloudformation execute-change-set --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name foundation-v1
aws cloudformation wait stack-create-complete --region "$AWS_REGION" --stack-name shop-lab
aws cloudformation describe-stacks --region "$AWS_REGION" --stack-name shop-lab \
  --query 'Stacks[0].Outputs' --output table
```

Không có bước nào ở trên được tự execute trong nhiệm vụ tạo file này.
`CREATE_COMPLETE` của foundation không có nghĩa API/website đã nghiệm thu.

## 3. DNS và SSH bằng Termius

Lấy Outputs `AlbDnsName`, tạo DNS **CNAME `origin` → AlbDnsName**, DNS-only,
không có `https://` và không thêm đường dẫn. Record này phải tồn tại và khớp
certificate trước khi gọi API qua CloudFront. Không trỏ origin CNAME về chính
CloudFront vì sẽ tạo vòng lặp.

Trong Termius tạo host bastion: `BastionPublicIp`, port 22, user **`ubuntu`**,
key pair đã chọn. Tạo host app với private IP, cùng user/key; đặt bastion là
SSH jump host/chaining. Không chép private key vào bastion. Cả hai dùng
**Ubuntu Server 24.04 LTS**, bootstrap APT HTTPS đã cấu hình trong YAML.
Sau khi SSH có thể xác nhận OS bằng `cat /etc/os-release`; nếu cần cài package
ngay, chờ `sudo cloud-init status --wait` kết thúc trước khi chạy APT.

Tra private IP của instance hiện thuộc ASG:

```bash
aws ec2 describe-instances --region "$AWS_REGION" \
  --filters Name=tag:aws:autoscaling:groupName,Values=REPLACE_ASG_OUTPUT \
            Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].[InstanceId,PrivateIpAddress]' --output table
```

IP bastion có thể đổi khi stop/start; lấy lại Outputs/EC2 public IP. Không pin
private IP app lâu dài vì ASG sẽ thay máy. SSM là đường quản trị dự phòng,
nhưng principal quản trị vẫn cần quyền `ssm:StartSession` phù hợp; không cấp
admin AWS cho instance profile để xử lý lỗi quyền.

## 4. Chuẩn bị DB, image và kho ảnh trước app mode

1. Lấy master password từ **RDS-managed Secrets Manager secret** bằng Console
   với tài khoản quản trị; không in vào log/chat. Không cấp ARN master secret
   làm `ApplicationEnvSecretArn`. Role app chỉ đọc đúng application secret.
2. Kết nối RDS từ bastion bằng MySQL 8 client, TLS verify identity, dùng RDS
   hostname thật và [CA bundle AWS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).
   Có thể cài Docker trên bastion rồi chạy MySQL client trong container:

   ```bash
   sudo cloud-init status --wait
   sudo apt-get -o DPkg::Lock::Timeout=180 -o Acquire::Retries=3 -o APT::Update::Error-Mode=any update
   sudo apt-get install -y --no-install-recommends docker.io ca-certificates curl
   sudo systemctl enable --now docker
   curl --fail --proto '=https' \
     https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o /tmp/shop-rds-ca.pem
   sudo docker run --rm -it \
     --mount type=bind,source=/tmp/shop-rds-ca.pem,target=/run/rds-ca.pem,readonly \
     mysql:8.4 mysql --host REPLACE_RDS_ENDPOINT --port 3306 --user shopadmin \
     --password --ssl-mode=VERIFY_IDENTITY --ssl-ca=/run/rds-ca.pem
   ```

   Nhập password ở prompt, không thêm password sau `--password`. Tạo hai
   tài khoản với hai password ngẫu nhiên khác nhau trong phiên quản trị kín:

   ```sql
   CREATE USER 'shop_runtime'@'10.0.%' IDENTIFIED BY '<RANDOM_RUNTIME_PASSWORD>' REQUIRE SSL;
   GRANT SELECT, INSERT, UPDATE, DELETE ON shop.* TO 'shop_runtime'@'10.0.%';
   CREATE USER 'shop_migration'@'10.0.%' IDENTIFIED BY '<DIFFERENT_RANDOM_MIGRATION_PASSWORD>' REQUIRE SSL;
   GRANT ALL PRIVILEGES ON shop.* TO 'shop_migration'@'10.0.%';
   ```

   Placeholder trên phải thay riêng; không lưu SQL chứa mật khẩu vào Git hoặc
   shell history. Mẫu `deploy/mysql/init/01-create-accounts.sh` dành cho MySQL
   container/socket, **không chạy nguyên xi trên RDS**. RDS đã tạo DB `shop`;
   Flyway khởi tạo schema. Dữ liệu cũ cần backup/restore có nghiệm thu riêng;
   template không tự chuyển database cũ sang RDS.

3. Lấy Output `ProductImagesBucketName` sau foundation. Bucket này đã được
   stack tạo; khác UI bucket, không tự tạo từ backend và không cấp quyền S3
   toàn tài khoản. Nếu có ảnh cũ, làm cutover theo S3-01: giữ object key,
   content type/bytes, đối chiếu số lượng/checksum; ngừng mutation ở lần đồng
   bộ cuối. Chưa đủ ảnh thì **dừng tại foundation**, không bật app để hiện ảnh lỗi.
4. Build/verify backend Java 21 rồi push image có version bất biến lên Docker
   Hub `t1r4m15u/...`. Chọn image thực tế từ repo hiện tại; không có image/tag
   giả định sẵn. Dùng digest `t1r4m15u/TEN_IMAGE@sha256:...`, kiến trúc amd64,
   image public để bootstrap pull không cần Docker Hub password. Private
   registry/ECR auth chưa được template cấu hình. Không dùng `latest`.
5. Tạo Secrets Manager secret trong cùng region/account, key mặc định AWS,
   chọn **plaintext** rồi lưu nội dung theo `application.env.example` đã thay
   giá trị thật. Đây là env-file TEXT, **không phải JSON**, không `export`,
   không quote quanh value, không multiline secret value. Có thể lấy ARN
   bằng Console; không chụp/copy nội dung secret vào báo cáo.
6. `SPRING_FLYWAY_USERNAME` là tên env đúng của dự án, không phải
   `SPRING_FLYWAY_USER`. Runtime DML khác migration DDL; JWT key ngẫu nhiên
   mạnh, ít nhất 64 ký tự, dùng chung giữa tất cả app EC2. CORS dùng đúng
   HTTPS `UiUrl` từ Outputs, không `*`. Không thêm tùy chọn JVM/config override.

Không đưa `AWS_*`, `MINIO_*`, `OBJECT_STORAGE_TYPE`, `S3_BUCKET`, `S3_REGION`
hoặc `APP_CATALOG_IMAGES_STORAGE*` vào application secret. Bootstrap inject
S3 region/bucket/type từ stack; DefaultCredentialsProvider trong container lấy
credential tạm qua EC2 instance role/IMDSv2 (hop limit 2). IAM app không có quyền
UI bucket hoặc xóa version. PUT/DELETE qua gateway endpoint; GET ký phải được
kiểm tra từ browser ngoài VPC. URL mặc định 15 phút, tối đa 1 giờ, có thể hết
hạn sớm hơn khi credential tạm hết hạn. Không log/copy URL ký vào báo cáo.
Bucket versioning giữ version cũ tối đa 30 ngày theo lifecycle; DELETE tạo
delete marker, không xóa version vĩnh viễn. Bucket giữ lại khi xóa stack vẫn
tính phí. Không có rule tự hết hạn ảnh hiện hành.

Bootstrap tạo truststore CA RDS rồi mount read-only vào container. Password
`changeit` trong JDBC URL chỉ bảo vệ **public CA truststore**, không phải DB
password. `sslMode=VERIFY_IDENTITY` vẫn kiểm tra CA và hostname. Secret được
ghi tạm trong `/run` root-only rồi xóa sau khi khởi chạy; Docker daemon/root
vẫn đọc được env của container, vì vậy quyền SSH/SSM/Docker là quyền nhạy cảm.
Rotation secret không tự cập nhật container đang chạy.

## 5. Bật backend trên ASG

Chỉ sau khi các prerequisite và gate MySQL 8.4 đạt, cập nhật
`parameters.local.json`: thêm/đặt `DeploymentMode=application`,
`ApplicationImage=<digest thật>`, `ApplicationEnvSecretArn=<ARN thật>` và tăng
`ConfigurationRevision` khi đổi secret. Giữ nguyên các parameter khác.

```bash
aws cloudformation create-change-set --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name application-v1 --change-set-type UPDATE \
  --template-body file://deploy/aws/shop-lab.yaml \
  --parameters file://deploy/aws/parameters.local.json --capabilities CAPABILITY_IAM
aws cloudformation wait change-set-create-complete --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name application-v1
aws cloudformation describe-change-set --region "$AWS_REGION" \
  --stack-name shop-lab --change-set-name application-v1
```

Duyệt diff, đặc biệt DB replacement, IAM/SG và image digest, rồi mới
`execute-change-set` với `application-v1`; theo dõi stack events và
`stack-update-complete`. Mỗi máy mới tự deploy; không chạy Compose chứa
MySQL/MinIO trên app ASG. Không điều chỉnh schema bằng tay để bỏ qua Flyway.

Nếu lỗi: xem stack events, `/var/log/cloud-init-output.log`, `docker ps` và
application CloudWatch log group. Không paste nguyên log chứa secret.
Bootstrap báo lỗi khi thiếu biến, dùng master DB user, AWS/storage override hoặc TLS
identity bị tắt. Signal local health không thay ALB health/purchase-flow test.

## 6. Build và upload React UI

React không chạy bằng `npm run dev` trên S3. Build ở máy dev/CI rồi chỉ upload
`dist/`; không upload source, `.env`, `node_modules` hoặc secret. Mọi `VITE_*`
được build vào trình duyệt, **không phải nơi chứa credential**.

Ví dụ **PowerShell trên máy hiện tại**, đã đăng nhập AWS CLI; thay hai Outputs:

```powershell
Set-Location D:/shop-ui
$env:VITE_API_BASE_URL='/api'
npm ci
npm run build
$uiBucket='REPLACE_UiBucketName'
$distributionId='REPLACE_CloudFrontDistributionId'
aws s3 sync dist/assets/ "s3://$uiBucket/assets/" --cache-control 'public,max-age=31536000,immutable' --region ap-southeast-1
aws s3 sync dist/ "s3://$uiBucket/" --exclude 'assets/*' --exclude index.html --cache-control 'public,max-age=300' --region ap-southeast-1
aws s3 cp dist/index.html "s3://$uiBucket/index.html" --content-type text/html --cache-control 'no-cache,max-age=0,must-revalidate' --region ap-southeast-1
aws cloudfront create-invalidation --distribution-id $distributionId --paths '/*'
```

Không dùng `sync --delete`: giữ assets phiên trước để tab cũ không hỏng.
Old hashed assets cần kế hoạch dọn có kiểm soát; lifecycle 7 ngày ở bucket
chỉ dọn **noncurrent versions**, không tự dọn mọi current object cũ.
Chỉ truy cập từ IP trong allowlist. Backend chưa chạy thì UI có thể tải được
nhưng danh sách sản phẩm/API vẫn lỗi; không gọi đó là deploy web hoàn chỉnh.

## 7. Nghiệm thu AWS và lab tải an toàn

- CloudFront `Deployed`, UI `/products` refresh không 404; JS/CSS đúng MIME.
- Ngoài allowlist nhận 403; đổi `X-Forwarded-For` không vượt được edge allowlist.
- Từ máy cá nhân không SSH trực tiếp private app, không vào RDS:3306, app:8080;
  SSH jump host vẫn hoạt động. UI S3 object direct URL nhận AccessDenied.
- Application mode có hai targets healthy. `/api` và `/api/...` không trả
  `index.html`; API 401/403/404 giữ nguyên, Bearer/cookie/query được chuyển tiếp.
- Login, CAPTCHA theo ngưỡng lỗi, product/cart/order, idempotency và JWT hoạt
  động qua nhiều node; không tạo charge/refund thật. Bootstrap ADMIN cần thao
  tác riêng có chủ đích, không lưu admin password trong template.
- Kiểm tra `SHOW SESSION STATUS LIKE 'Ssl_cipher'`, grants runtime/migration,
  Flyway version, product-image URL và dữ liệu trước/sau khi thay một instance.
- Theo dõi CPU, CPUCreditBalance, ALB TargetResponseTime/5xx/UnHealthyHostCount,
  ASG desired/in-service, RDS connections/storage, log/egress và billing.
  Load test ứng dụng phải có trần lưu lượng, thời lượng ngắn, người giám sát
  và điều kiện dừng khi 5xx/latency/DB connection/cost tăng bất thường.

**Không chạy flood/DDoS/SYN flood từ EC2 chỉ vì đây là tài khoản của bạn.**
Target tracking không phải DDoS protection và không bảo đảm app không sập.
Load test ứng dụng có kiểm soát khác với DDoS simulation. Trước mọi DDoS
simulation phải đáp ứng [AWS DDoS Simulation Testing Policy](https://aws.amazon.com/security/ddos-simulation-testing/)
và quy trình chấp thuận/vendor liên quan. Template không mua Shield Advanced,
không cấp phép thử DDoS và không chứa script tấn công. Xem thêm
[AWS load testing guidance](https://docs.aws.amazon.com/whitepapers/latest/aws-best-practices-ddos-resiliency/load-testing.html).

## Rollback và kết thúc lab

- Rollback app bằng image digest/cấu hình đã biết tốt rồi tăng
  `ConfigurationRevision`, review change set và rolling update. Không đổi
  `DeploymentMode` về foundation nếu mục tiêu là giữ app phục vụ; foundation
  cố ý không chạy app. Secret cũ phải được phục hồi có chủ đích vì rollback
  CloudFormation không hoàn tác nội dung secret bạn sửa ngoài stack.
- Migration đã áp dụng không tự rollback khi image rollback; kiểm tra tính
  tương thích hoặc roll-forward. Không sửa checksum migration đã chạy.
- Không đổi VPC CIDR, DBName, DB identifier hoặc engine major để thử rollback.
  Review mọi `Replacement` của RDS/VPC/subnet trước khi execute.
- Xóa stack phải do bạn chủ động quyết định sau backup/export và kiểm tra
  nghiệm thu. RDS có snapshot khi xóa, UI bucket/application log group retain;
  các bản giữ lại **vẫn có thể tính phí**. RDS-managed master secret không được
  coi là bản backup password sau khi xóa DB; restore cần cấu hình credential
  lại. Không cung cấp lệnh xóa bucket/snapshot hàng loạt.
- Chỉ stop EC2 không kết thúc chi phí NAT/ALB/RDS; ASG còn có thể thay máy đã
  stop. Kiểm tra resource còn lại và billing sau khi chủ động kết thúc lab.

## Bằng chứng kiểm tra và giới hạn nghiệm thu

### AWSLAB-01D — Chuẩn bị thử lại ngày 19/10/2026

- Chủ tài khoản xác nhận tạo stack mới `shop-aydibro-v2`, giữ stack/tài
  nguyên cũ. Parameters đã đối chiếu Console; ACM certificate cho
  `shop.aydibro.click` đã `Issued` tại Singapore. Không thay đổi certificate.
- Chạy lại **21/21 Node checks, 0 failure/skipped** và diff check đạt;
  SHA-256 YAML `7c452afded69f30fff1bc5260c38b0cf2ef6c31d5f54c0f6ccc3b829ace7c5cf`.
- Chủ tài khoản đã upload `D:/shop/deploy/aws/shop-lab.yaml` trực tiếp sau
  khi công cụ không mở được file chooser. Đã tạo change set
  `foundation-v2-20261019` cho `shop-aydibro-v2`, trạng thái
  `CREATE_COMPLETE`, execution `AVAILABLE`; 55 resource changes đều `Add`.
- Đọc nguyên văn template từ bảng DOM của AWS và đối chiếu SHA-256: khớp
  chính xác source local đã test, gồm `BackupRetentionPeriod: 1`. Banner
  deployment validations báo đạt; chưa chứng minh RDS được provision.
- Đã mở hộp xác nhận Execute và giữ `Roll back all stack resources` +
  `Use deletion policy`. Chủ tài khoản xác nhận Execute ngay tại bước cấp
  quyền; AWS đã báo change set executed lúc **00:27:13 ngày 19/10/2026
  (UTC+7)**. Không Execute lặp hoặc tạo stack thứ ba.
- **Lỗi gốc mới, 00:29:57:** `UiDistribution` `CREATE_FAILED`, CloudFront
  trả 403/`AccessDenied`: `Your account must be verified before you can add
  new CloudFront resources.` AWS yêu cầu liên hệ Support để xác minh account.
  Request ID `4c52a7fa-371f-41e3-ad8d-f4546344fee7`; không đưa credential,
  private key hoặc application env vào case.
- Stack vào `ROLLBACK_IN_PROGRESS` lúc **00:29:58**. Các `CREATE_FAILED` của
  Database, bastion, instance profile và app routes ghi `Resource creation
  cancelled`, không phải lỗi độc lập đã được chứng minh. Database từng vào
  `CREATE_IN_PROGRESS`, chưa từng nghiệm thu `CREATE_COMPLETE`/restore;
  không kết luận retention 1 ngày đã vượt mọi giới hạn account.
- Lần kiểm kê trong rollback: Database `DELETE_IN_PROGRESS`; ALB/listener,
  DB networking và một số resource còn `CREATE_COMPLETE`, chưa thu hồi xong.
  `ProductImagesBucket` mới và application log group mới `DELETE_SKIPPED`;
  UI bucket mới còn `CREATE_COMPLETE` tại lần đọc. Đây không phải kiểm kê
  toàn tài khoản hoặc bằng chứng hóa đơn đã ngừng. Tài nguyên stack cũ giữ
  nguyên; không tự xóa các resource retain hay snapshot để dọn phí.
- Read-only STS cho thấy CLI đăng nhập tài khoản khác với Console; không
  dùng credential đó để tạo stack, không đổi profile/login tự động.
- Tại lần thử này chưa kiểm chứng restore, không xóa resource cũ,
  không nâng plan, không push/merge hoặc chạy giao dịch thật. Cập nhật RDS
  sau rollback xem phần dưới.

#### Cập nhật Support và rollback — 19/10/2026

- Chủ tài khoản đã duyệt gửi mô tả lỗi/kiến trúc S3-React-ALB và tạo case.
  Support Console xác nhận case được tạo lúc **00:54:51 (UTC+7)**, nhóm
  `Account and billing → Account Activation → Account Verification`,
  severity `General question`, language English, kênh Web/email,
  trạng thái `Unassigned`. Không thêm người nhận; không gửi credential,
  payment details, giấy tờ danh tính, file env hoặc private key. Mã case và
  email tài khoản chỉ bàn giao riêng, không lưu vào repo public.
- Không đổi account/support plan, không cấp IAM, không bật DevOps Agent
  hoặc tạo hạ tầng thay cho việc xác minh. Case đã gửi không có nghĩa AWS
  đã gỡ hạn chế; không đánh dấu CloudFront hoặc foundation hoàn tất.
- Stack `ROLLBACK_FAILED` lúc **00:34:23**. Lỗi `Database DELETE_FAILED`
  lúc 00:32:14: không thể tạo snapshot vì instance chưa `available` khi
  rollback chạy. Không bỏ `DeletionPolicy: Snapshot` để xử lý lỗi.
- Kiểm tra read-only sau đó: RDS đã **Available**, backup
  `Enabled (1 Day)`, copy tags enabled; một automated snapshot `Available`.
  Đây là bằng chứng retention 1 ngày được cấu hình thực tế ở tài khoản này,
  không phải test restore, dữ liệu nghiệp vụ hoặc tương thích Shop/MySQL 8.4.
- Kiểm kê khi **v2 còn `ROLLBACK_FAILED`**: ALB/NAT/EC2 `DELETE_COMPLETE`; Database
  `DELETE_FAILED`, VPC/DB subnets/parameter group/security groups còn lại.
  Hai S3 bucket và application log group mới `DELETE_SKIPPED`. RDS khi đó còn chạy
  có thể phát sinh phí; không suy ra toàn tài khoản hết phí từ kiểm kê này.
- Sau khi xem phương án tại hộp xác nhận Delete, chủ tài khoản yêu cầu
  tiếp tục thu hồi riêng v2. Kiểm tra lại đúng stack/account/region, template
  thật vẫn Snapshot/Retain và RDS `Available`; đã xác nhận xóa lúc
  **09:32:21 (UTC+7)**, không Express/force. Không tác động `shop-aydibro`
  cũ hoặc xóa snapshot/bucket đã giữ lại.
- Stack đã **`DELETE_COMPLETE` lúc 09:37:05 (UTC+7)**. RDS Console tại
  Singapore không còn instance; `Database`, DB networking/VPC, ALB/NAT/EC2
  và các resource không-retain của v2 đều `DELETE_COMPLETE`.
- Final manual snapshot
  `shop-aydibro-v2-snapshot-database-hh54mobzw1xq` đã **Available**, encrypted,
  đúng DB nguồn `shop-aydibro-v2-database-jq5ztaaegnzm`; Console ghi thời
  điểm tạo cuối là 09:36 (UTC+7), MySQL 8.4.11, storage 20 GiB. Chưa test
  restore; không dùng snapshot này để kết luận Shop tương thích MySQL 8.4.
- Ba resource v2 được giữ nguyên (`DELETE_SKIPPED`):
  `shop-aydibro-v2-uibucket-mrei9h5f1q0t`,
  `shop-aydibro-v2-productimagesbucket-5wzinp6sw6kq` và
  `shop-aydibro-v2-ApplicationLogGroup-hPY691VLZRBg`.
  Không xóa stack cũ hoặc resource/snapshot đã giữ lại; kiểm tra stack cũ
  vẫn `ROLLBACK_COMPLETE` sau khi v2 thu hồi xong.
- Chạy lại **21/21 Node checks, 0 failure/skipped** trong lượt cleanup;
  SHA-256 YAML không đổi so với bản đã Execute; không sửa YAML, chạy
  giao dịch/migration hoặc push/merge. AWSLAB-01C hoàn tất phạm vi thu hồi,
  không chuyển AWSLAB-01D/foundation thành đạt.
- Xóa DB sẽ dừng endpoint; khôi phục từ snapshot cần tạo DB mới và cấu hình
  credential, không phải undo ngay. Snapshot/retained resources vẫn có thể
  tính phí; kết quả trên không phải kiểm kê toàn account hoặc hóa đơn.
  Support correspondence hiện chưa có phản hồi mới từ AWS.

#### AWSLAB-01P — Kết quả cleanup được duyệt riêng

- Sau khi xem danh sách resource và hộp xác nhận snapshot, chủ tài khoản
  trả lời xác nhận xóa vĩnh viễn, gồm bản sao DB và nội dung/version S3.
  Đây là phê duyệt mới, thay cho quyết định retain của AWSLAB-01C trong
  đúng các target đã liệt kê; không sửa DeletionPolicy trong YAML.
- Đã xóa thành công bốn bucket của hai stack:
  `shop-aydibro-productimagesbucket-icwy9wfm7twc`,
  `shop-aydibro-uibucket-nmoclokfm9ge`,
  `shop-aydibro-v2-productimagesbucket-5wzinp6sw6kq`,
  `shop-aydibro-v2-uibucket-mrei9h5f1q0t`.
  S3 Console báo thành công từng bucket; lọc `shop-aydibro` còn 0 matches.
  Không cần thao tác Empty hoặc thay versioning để xóa các bucket này.
- CloudWatch báo xóa thành công hai log group
  `shop-aydibro-ApplicationLogGroup-vvI81KE7Tobi` và
  `shop-aydibro-v2-ApplicationLogGroup-hPY691VLZRBg`; danh sách log groups trống.
- RDS báo xóa thành công final manual snapshot
  `shop-aydibro-v2-snapshot-database-hh54mobzw1xq`; Manual snapshots còn 0.
  System snapshots sau đó cũng còn 0, Retained backups báo không có bản
  backup giữ lại; không gửi lệnh xóa automated snapshot riêng.
  Snapshot `Available` ghi ở AWSLAB-01C là bằng chứng lịch sử, không
  còn là nguồn restore hiện hành sau lần xóa này.
- Stack cũ `shop-aydibro` được Delete lúc **09:48:05**, đạt
  **`DELETE_COMPLETE` lúc 09:48:07 (UTC+7)**; v2 đã `DELETE_COMPLETE` trước đó.
  Không dùng Express/force, không tạo lại foundation/application hoặc
  đụng domain/certificate/key pair và resource tạo tay.
- Tại nghiệm thu AWSLAB-01P, giữ hai bucket `cf-templates-yw9mur8k4zka-ap-southeast-1`,
  `cf-templates-yw9mur8k4zka-ap-southeast-2` và bucket tạo tay
  `d-s3-shop-demo-sk`: không thuộc bốn bucket đã duyệt. Hai bucket template
  có thể phục vụ các lần upload/stack khác; cần xác nhận target riêng nếu
  muốn xóa tiếp. Không báo toàn account đã sạch hoặc không còn chi phí.
- Không đổi source/API/schema/YAML/workflow, không chạy lại Maven/Node,
  không push/merge Git trong lượt xóa resource retain; gate source 21/21
  của lượt AWSLAB-01C vẫn là bằng chứng lịch sử, không phải test vừa chạy.
- AWSLAB-01P hoàn tất đúng phạm vi resource đã duyệt; kiểm tra tài liệu
  bằng diff/whitespace. Hai bucket template khi đó còn chờ duyệt riêng;
  quyết định và kết quả mới xem AWSLAB-01T bên dưới.

#### AWSLAB-01T — Kết quả xóa hai bucket template được duyệt riêng

- Sau khi xem hai tên bucket/region và cảnh báo có thể chứa template của
  stack khác, chủ tài khoản xác nhận xóa vĩnh viễn cả hai bucket, toàn bộ
  dữ liệu/version bên trong. Không mở rộng sang bucket tạo tay hoặc dịch vụ khác.
- Singapore: `cf-templates-yw9mur8k4zka-ap-southeast-1`, Empty thành công
  **3 object, 126,4 KB, 0 object lỗi**; sau đó Delete bucket thành công.
- Sydney: `cf-templates-yw9mur8k4zka-ap-southeast-2`, Empty thành công
  **1 object, 42,1 KB, 0 object lỗi**; sau đó Delete bucket thành công.
  Không rời trang khi Empty còn chạy; không thay IAM/policy/versioning để ép xóa.
- Danh sách S3 **General purpose buckets / All AWS Regions**, không đặt bộ
  lọc, chỉ còn **1 bucket** `d-s3-shop-demo-sk` ở Sydney. Hai tên template
  không còn; bucket tạo tay không bị chọn/xóa/empty. Đã lưu screenshot
  Empty từng bucket và danh sách sau Delete.
- AWSLAB-01T hoàn tất trong phạm vi đã duyệt; object/version/bucket đã xóa
  không có undo. Không khẳng định mọi tài nguyên AWS hoặc chi phí toàn
  account đã hết; không xóa domain/certificate/key pair, Support case hoặc
  template local. Không tự dựng hạ tầng mới thay cho chủ tài khoản.
- Chỉ cập nhật README/runbook; kiểm tra diff/whitespace và hash YAML
  không đổi, không chạy lại Maven/Node hoặc push/merge Git.

**Bước cần chủ tài khoản:** theo dõi case trong [AWS Support Center](https://console.aws.amazon.com/support/home#/)
và tự cung cấp thông tin xác minh nếu AWS yêu cầu qua kênh chính thức;
không gửi thông tin thanh toán/danh tính vào chat hoặc Git. Xem
[quy trình hỗ trợ chính thức](https://docs.aws.amazon.com/awssupport/latest/user/create-support-interaction.html)
và [Snapshot/Retain khi xóa stack](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-attribute-deletionpolicy.html).
Không sửa YAML hoặc cấp AdministratorAccess để thay cho việc AWS xác minh.
Chỉ thử lại sau khi AWS xác nhận gỡ hạn chế, rollback kết thúc, kiểm kê
resource giữ lại và được chủ tài khoản duyệt phương án cùng chi phí.

### AWSLAB-01R — Ngày 19/10/2026

- Test retention mới thất bại với YAML cũ (`3 !== 1`), sau khi sửa đạt
  **21/21 Node checks, 0 failure/skipped**, gồm Bash bootstrap mock/syntax,
  Python guard, network/security và AWS resource specification read-only.
- AWS Console tại `ap-southeast-1` xác nhận stack `shop-aydibro` đã
  `ROLLBACK_COMPLETE`, không cập nhật trực tiếp được. Resources ghi
  `UiBucket`, `ProductImagesBucket`, `ApplicationLogGroup` là `DELETE_SKIPPED`;
  Database, NAT và ALB là `DELETE_COMPLETE`. Đây là trạng thái của stack,
  không phải kiểm kê mọi tài nguyên hoặc hóa đơn trong tài khoản.
- Chỉ sửa bản YAML local; chưa tạo lại stack, chưa đổi plan hoặc xóa các
  tài nguyên giữ lại. Chưa xác nhận AWS chấp nhận retention 1 ngày; không coi
  test schema là kiểm tra giới hạn Free account hay nghiệm thu deploy.
- Không đổi Java/schema/Payment, không chạy lại Maven/Sonar và không cập
  nhật coverage backend từ kết quả kiểm thử hạ tầng. Chưa commit/push/merge.

### S3-01 — Ngày 18/10/2026

- **20/20 Node checks đạt, không skipped**: giữ gate AWSLAB-01U, thêm bucket
  ảnh private/tách UI/versioning/retain/TLS, IAM giới hạn prefix và bootstrap
  S3 bằng role. Guard từ chối static AWS credentials/endpoint override,
  storage identity override và cấu hình MinIO trong application secret.
- AWS `validate-template` chấp nhận bản YAML mới, yêu cầu `CAPABILITY_IAM`;
  schema kiểm tra với AWS resource specification hiện hành. Không tạo hay
  execute change set, không thay stack đang chạy.
- Backend full Maven `clean verify` đạt **477 test, 0 failure/error/skipped**,
  gồm 18 test S3. Line **91,06%**, branch **69,91%** toàn backend; branch
  riêng S3 **98,81%**. Có MySQL 8.0.46 và MinIO/Silo thật qua Testcontainers,
  không coi HTTP fixture hoặc mock là smoke AWS.
- Local/prod/lab Compose config và diff check đạt. Chưa build/push container
  mới, chuyển ảnh MinIO, chạy EC2/IAM/VPC endpoint smoke hay kiểm chứng MySQL
  8.4. Xem [gate/cutover/rollback S3-01](../../docs/product-images-s3.md).

### AWSLAB-01U — Ngày 16/10/2026, tại máy dev

- Sau AWSLAB-01U: **19/19 Node checks đạt, không skipped**, gồm
  YAML/intrinsics, dependency graph, network
  isolation, SG, ASG bounds, RDS/S3, no-cache API và CloudFront Function fixtures.
- Bổ sung Ubuntu AMI/root/SSH contract, OS guard, APT HTTPS/idempotency và
  bootstrap mock thành công/lỗi package. Mock không gọi AWS, Snap/APT thật,
  Docker hoặc systemd; chỉ chỉnh fixture trong thư mục tạm riêng của test.
- Bash `-n` của cả bastion/app; Python guard thực thi bằng runtime có sẵn,
  không cài Python vào hệ thống người dùng; case thiếu biến/master user/TLS
  bypass/MinIO HTTP bị từ chối.
- Đối chiếu required fields/property names với AWS CloudFormation Resource
  Specification tải **read-only** từ AWS, không lưu credential.
- AWS `cloudformation validate-template` trả thành công và yêu cầu
  `CAPABILITY_IAM`. Đây là kiểm tra template, không phải resource deployment.
- AWS RDS read-only xác nhận `8.4.11`, family `mysql8.4`, status `available` và
  `db.t4g.micro` hỗ trợ gp3 tại `ap-southeast-1`. Region khác phải kiểm tra lại.
- AWS SSM/EC2 read-only xác nhận Ubuntu AMI `ami-09fad7bb72d4e6685`, tên
  `ubuntu-noble-24.04-amd64-server-20261004`, owner Canonical `099720109477`,
  x86_64, available, root `/dev/sda1` 8 GiB tại `ap-southeast-1`.
  Đây là bằng chứng lúc kiểm tra, **không hard-code AMI ID này** vào template;
  public parameter `current` có thể đổi sau đó.
- Không chạy Maven/Sonar vì không sửa Java/schema nghiệp vụ; không cập nhật
  coverage bằng kết quả test hạ tầng. Không tạo/execute change set, không
  deploy, không push/merge; chưa có bootstrap/ALB/RDS smoke test trên AWS.

Để chạy lại Node checks, cần Node 20+ và `js-yaml`. Có thể trỏ
`SHOP_YAML_MODULE` vào dependency đã có, không cần thêm dependency vào backend.
Các biến `SHOP_BASH`, `SHOP_PYTHON` bật kiểm tra thực thi; thiếu chúng test
sẽ ghi SKIP, không được báo tất cả đạt. `SHOP_FETCH_CFN_SPEC=1` tải spec read-only.

Ví dụ Bash, cài parser vào thư mục tạm **chỉ khi chưa có**:

```bash
check_dir=$(mktemp -d)
npm install --prefix "$check_dir" --ignore-scripts --no-audit --no-fund js-yaml@4
SHOP_YAML_MODULE="$check_dir/node_modules/js-yaml" \
SHOP_BASH="$(command -v bash)" SHOP_PYTHON="$(command -v python3)" \
SHOP_FETCH_CFN_SPEC=1 node --test ci/aws-template.test.cjs
git diff --check
```

Phạm vi hoàn thành ở đây là **file hạ tầng + runbook + kiểm tra template**.
Bước tạo foundation đã được chủ tài khoản thử và rollback; xem AWSLAB-01R
ở trên. Trước khi thử lại cần kiểm tra parameters, ACM certificate, ngân sách,
giới hạn account và tài nguyên giữ lại; phải được xác nhận trước khi tạo/xóa.
Bật ứng dụng vẫn phụ thuộc gate
MySQL 8.4, image/secret thật và cutover ảnh S3/IAM đã nghiệm thu. Không đánh dấu website,
Payment hoặc toàn bộ dự án hoàn thành từ nhiệm vụ này.
