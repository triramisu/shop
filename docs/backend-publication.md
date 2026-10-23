# PUB-BE-01 — Xuất bản backend độc lập

## Hợp đồng tám phần — 19/10/2026

1. **Phạm vi:** theo ủy quyền hiện hành, rà soát và xuất bản thay đổi backend local lên `triramisu/shop`, thông qua nhánh/PR và CI trước khi merge `main`. Không tự triển khai AWS, push Docker Hub, mở thanh toán thật hoặc xuất bản repo UI trong lượt này.
2. **Đầu vào/ownership:** baseline `origin/main` là `ba555a4`; working tree có việc đưa backend ra gốc, tách frontend, lịch sử đơn, refund core mặc định tắt, S3 adapter, VNPay sandbox opt-in và tài liệu tương ứng. Giữ source UI độc lập ở `D:/shop-ui`. Không gom hai file skill đang sửa/stage sẵn vào commit ứng dụng.
3. **Bất biến:** đối chiếu di chuyển với source cũ, giữ lịch sử Git; không sửa migration cũ hoặc trạng thái tiền. Dev dùng fake, prod mặc định none, refund tạo mới tắt. M5 chưa hoàn tất, chưa có SIT/IPN VNPay thật.
4. **An toàn:** scan secret/dependency, rà API/auth/ownership/idempotency/concurrency và cấu hình fail-closed. Không đọc/in credential thật vào báo cáo; không commit env thật, key, runtime data hoặc log. Bỏ `.env.lab` khỏi Git nhưng giữ file local. Không force-push, bypass check hoặc thay branch protection/biến deploy.
5. **Đầu ra:** commit có phạm vi rõ, README/bằng chứng kiểm thử, nhánh remote, PR và merge chỉ khi `verify`/`dependency-review` đạt. Chỉnh đường dẫn workflow hiện có để khớp root layout không phải mở rộng CI/CD.
6. **Ca kiểm tra:** fresh/upgrade V25, MySQL/MinIO, kiến trúc module, ownership/security API, IPN sai chữ ký/amount/reference và callback đồng thời; Compose example/shell/hạ tầng; scan nội dung chuẩn bị commit, không phát sinh credential trong source.
7. **Gate:** full Maven `clean verify` không exclusion, Spotless/JaCoCo, Compose config, shell syntax/action lint, test template, secret/dependency scan và diff check. Sau push chờ CI đúng commit, sau merge kiểm tra workflow của merge commit. Không gọi kết quả là hoàn hảo tuyệt đối hoặc tăng coverage giả; branch >80% vẫn là mục tiêu cuối dự án.
8. **Rollout/rollback:** source publication không đồng nghĩa deploy. Kiểm tra các cờ CD/deploy trước merge, không kích hoạt chúng. Rollback bằng revert PR hoặc artifact trước, không reset/xóa dữ liệu. Chỉ đồng bộ local main sau hậu kiểm xanh; giữ mọi sửa đổi skill chưa thuộc task.

### Cấu hình để người vận hành điền sau

Theo xác nhận của người dùng, không cần credential hoặc DNS thật để xuất bản source. Các giá trị DB, JWT, CORS và VNPay tiếp tục đọc từ env; chỉ commit file mẫu/placeholder. Khi deploy, copy `deploy/.env.prod.example` thành `deploy/.env.prod` ngoài Git rồi điền giá trị riêng. Không thay validation production để chấp nhận credential trống: build/test độc lập bằng fixture, còn chạy production phải có đủ cấu hình thật. Khi chưa cấu hình thanh toán giữ `PAYMENT_PROVIDER_TYPE=none`, `PAYMENT_REFUND_ENABLED=false`.

AWS DDoS lab không phải môi trường payment SIT: giữ provider none và không đưa merchant secret vào mẫu lab. File `.env.lab` thật vẫn còn trên máy, nhưng không còn thuộc source xuất bản. Không cần gửi secret trong chat.

## Bằng chứng

Nghiệm thu local hoàn tất ngày 19/10/2026. Chưa push/merge tại thời điểm ghi bằng chứng này; kết quả xuất bản được đối chiếu bằng PR và workflow đúng SHA trên GitHub.

- GitHub được kiểm tra read-only: `main` hiện không có branch protection/ruleset; vẫn thực thi PR + hai gate CI thủ công, không sửa quyền hoặc bypass. `CD_ENABLED`/`DEPLOY_ENABLED` chưa được cấu hình, không bật deploy trong task này.
- Đã bỏ `.env.lab` khỏi index và thêm ignore cho env thật, giữ file local. Các giá trị từng commit trong file cũ phải được coi là đã công khai: tạo credential mới trước khi triển khai, việc bỏ file khỏi HEAD không xóa Git history. Không rewrite history hoặc tự đổi mật khẩu DB/JWT của môi trường đang chạy.
- Secret scan Trivy tìm thấy một credential trong `.idea/workspace.xml` bị Git ignore; không in giá trị, file không được theo dõi hoặc đưa vào commit. Không dùng kết quả này để xác nhận credential chưa từng lộ ngoài Git.
- Trivy `0.75.0` đúng digest CI, cache Maven read-only, offline Maven resolution, severity MEDIUM/HIGH/CRITICAL + ignore-unfixed: 0 finding trong `pom.xml`. Lượt đầu thiếu cache gặp Maven Central 429, đã chạy lại với cache đầy đủ; không bỏ gate hoặc đổi severity.
- Compose prod/lab example, shell syntax ba script deploy/init/setup và actionlint CI/CD đạt.
- Gate hạ tầng bật Python guard + AWS schema download phát hiện env mẫu thêm VNPAY_* rỗng, trái parser fail-closed và lab cố ý ép provider none. Đã loại các entry không áp dụng khỏi mẫu và thêm assertion giữ chặt parser; chạy lại toàn bộ, không skip ca lỗi.
- Gate hạ tầng sau sửa: **21/21 đạt, 0 skip**, bao gồm Bash bootstrap mock, embedded Python guard và đối chiếu schema tài nguyên AWS chính thức. Python dùng runtime có sẵn, js-yaml 4.1.1 cài vào thư mục công cụ tạm ngoài repo, không thêm dependency của ứng dụng.
- Commit cấu trúc riêng `79167c5` di chuyển nguyên blob backend và gỡ bản frontend cũ; chức năng nằm ở commit tiếp theo để review không bị che bởi rename. Tạo commit cấu trúc qua index tạm để giữ nguyên staged skill của người dùng; lượt thử đầu lỗi CRLF giao thức index-info đã dừng trước commit, chạy lại bằng UTF-8/LF đạt, real index được kiểm tra hash không đổi. Đã sửa executable bit cho Maven wrapper và ba script deploy/init/setup để dùng được sau clone Ubuntu.
- Full `mvnw.cmd --batch-mode clean verify` với Java 21.0.12.1 hoàn tất lúc 15:45:02 +07 ngày 19/10/2026: **537 test, 0 failure, 0 error, 0 skip**, 136 suite; Spotless và JaCoCo gate đạt. Bao gồm Testcontainers MySQL 8.0.46, MinIO, fresh/upgrade V25, security/OpenAPI và IPN đồng thời. Không exclusion, không hạ threshold.
- JaCoCo full regression: line **91,21%** (6.437/7.057), branch **70,20%** (2.057/2.930). Đây là bằng chứng cho bản source xuất bản, chưa đạt mục tiêu branch >80% cuối dự án và không đóng milestone M5.
- JAR được tạo lại ở `target/shop-0.0.1-SNAPSHOT.jar`; artifact build không thuộc Git. Việc push source không push Docker image, không chạy deployment hoặc gọi giao dịch thật.

## Biên bản đóng xuất bản source — 23/10/2026

Hợp đồng bổ sung cho tài liệu bàn giao:

1. Mục tiêu: chốt bằng chứng PR/CI và phân biệt snapshot nghỉ với người dùng
   tự bật lại AWS. Chỉ sửa README/docs, không đổi code, workflow hoặc hạ tầng.
2. Đầu vào: main `02da697` chứa query #43, worker #44 và USER test #45; giữ
   mọi thay đổi AWS/skill dirty trong `D:/shop` ngoài commit tài liệu.
3. Mỗi trạng thái runtime phải có thời điểm; EC2 InService không thay ALB
   Healthy, GET public không thay login/ownership hoặc payment SIT.
4. Không đưa credential, raw log, financial report hoặc instance/account IDs
   vào bản public này. Không đổi quyền, CD/deploy flags, IAM, secret hoặc DB.
5. Đầu ra: README và biên bản query/worker/USER/publication cập nhật nhất quán.
6. Đối chiếu đúng SHA/check/base và HTTP status; không suy diễn nguyên nhân
   lỗi quá khứ nếu chưa có log/health reason tại thời điểm lỗi.
7. Gate docs: diff check, Markdown fence/link nội bộ, diff không có file runtime;
   PR vẫn phải đủ hai check và hậu kiểm main, không thay workflow để skip gate.
8. Rollback bằng revert tài liệu; không tác động dữ liệu/runtime. Báo cáo chi
   phí đầy đủ giữ local riêng, không gộp vào repo public.

| Phần source | PR | Merge SHA | Required checks / CI main |
|---|---|---|---|
| VNPay query | [#43](https://github.com/triramisu/shop/pull/43) | ca37e4c | [CI main xanh](https://github.com/triramisu/shop/actions/runs/39885449276) |
| VNPay worker/V26 | [#44](https://github.com/triramisu/shop/pull/44) | d599416 | [CI main xanh](https://github.com/triramisu/shop/actions/runs/39886151065) |
| USER/token/ownership | [#45](https://github.com/triramisu/shop/pull/45) | 02da697 | [CI main xanh](https://github.com/triramisu/shop/actions/runs/39887323114) |

Cả ba PR đều qua `verify` và `dependency-review` trên HEAD tương ứng trước
merge. Dependency-review skipped ở push main là đúng điều kiện workflow;
không phải đã bỏ required check của PR. Main được bảo vệ: PR, hai check từ
GitHub Actions, strict up-to-date, enforce admin, không force-push/delete;
không bắt người duyệt thứ hai. Chủ repo tự xác minh GitHub khi được yêu cầu.
Quy trình từng thao tác ở [biên bản USER](demo-user-authorization-matrix.md#quy-trình-github-đã-dùng-để-xuất-bản-an-toàn).

Full regression source kết hợp: **708 test**, 0 failure/error/skipped, kết thúc
08:51:25 UTC+7; MySQL 8.0.46, MinIO, H2, V26 fresh/upgrade, Modulith/ArchUnit,
Spotless/JaCoCo và Compose đạt. Line **91,73%**, branch **72,36%**; mục tiêu
>80% branch vẫn mở, không hạ threshold. Tree `eed3d6d` trùng merge `02da697`.
Không chạy lại Maven local chỉ cho sửa Markdown; CI vẫn kiểm đầy đủ theo rule.

ASG nghỉ tại snapshot 08:06–08:12; người dùng xác nhận tự mở lại, activity ghi
nhận 08:33:02. Lúc 09:00 UTC+7: ASG 2/2/4, hai app InService/SSM Online và
hai ALB target Healthy; bastion running. Health check HTTP 8080, đường dẫn
`/actuator/health`, matcher 200, timeout 5 giây. Ba GET HTTPS UI/catalog/my-info
trả lần lượt **200/200/401**; 401 anonymous là mong đợi. Agent không bật/tắt,
restart, gửi SSM command, thay health matcher hoặc deploy. Không có bằng chứng
root cause cho Unhealthy trước đó; không đổi cấu hình khi trạng thái đã phục hồi.

Source mới chưa build/push image hoặc rollout lên AWS; V26 chưa áp RDS 8.4,
query/worker/payment vẫn tắt. Không đóng M5, live USER/IDOR, backup restore,
VNPay SIT hoặc go-live. Trước deploy cần gate image/DB/runtime/rollback riêng;
không trộn load/DDoS test với sandbox payment. Dữ liệu chi phí và credit có
thời gian/phạm vi riêng ở báo cáo local, không công khai trong PR này.
