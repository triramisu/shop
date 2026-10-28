# DEMO-01B.1 — Ma trận USER/token/ownership tại local

[Mục lục tài liệu](README.md) · [Triển khai](../deploy/README.md)

## Hợp đồng tám phần

1. Mục tiêu: chứng minh contract anonymous/USER/admin-denied trên cùng bộ API,
   token sai/refresh token/revoked token không dùng làm access token và hai USER
   không đọc/sửa dữ liệu nhau. Không gọi production hoặc VNPay, tạo ADMIN, sửa
   role bằng SQL, dùng password chung trên web public hoặc thay security policy.
2. Identity sở hữu đăng ký/token; Catalog public; Order sở hữu cart/order/payment
   query. Dùng API thật qua MockMvc và fixture test thuộc Order; không thêm phụ
   thuộc production xuyên module, không sửa schema/Flyway V1–V25.
3. Ma trận public 200, private anonymous 401, USER own API 200, USER admin 403.
   Order/payment khác chủ và UUID không tồn tại phải cùng 404/1310; cart item
   khác chủ/missing cùng 404/1301. Không đổi response chỉ để làm test xanh.
4. Không dùng JWT/secret/account thật. Register/login tạo USER mặc định qua API;
   fixture dùng password test sẵn có, chỉ trong test profile. Không publish
   credential, token hoặc presigned URL; không test refund/payment thật.
5. Đầu ra: integration-test ma trận liên module và biên bản/README; không có
   thay đổi controller/service, dependency, profile dev/prod hoặc migration.
6. Kiểm cả response và side effect: foreign mutation không đổi cart của chủ;
   history chỉ chứa đơn principal, request ownerSubject không đổi ownership;
   logout revoke làm access token cũ mất hiệu lực, kể cả khi gửi vào API public.
7. Nghiệm thu local: focused tests, regression Identity/Order/Catalog/Modulith,
   Spotless và diff check. Chạy full verify khi Docker sẵn; chỉ MySQL test mới
   được dùng để kết luận locking/concurrency. MockMvc/H2 không là bằng chứng live.
8. Không AWS mutation hoặc deploy. Rollback test/docs không đổi dữ liệu runtime.
   Giữ lab nghỉ; live USER/SIT và merge/CI là gate riêng, không tự đánh dấu task
   cha DEMO-01 hay Payment hoàn tất từ gate local.

## Bộ API được kiểm tra

| GET endpoint | Anonymous | USER |
|---|---:|---:|
| /api/catalog/categories | 200 | 200 |
| /api/catalog/products | 200 | 200 |
| /api/auth/my-info | 401 | 200 |
| /api/cart | 401 | 200 |
| /api/orders | 401 | 200 |
| /api/admin/catalog/products | 401 | 403 |
| /api/admin/inventory/stock-items | 401 | 403 |
| /api/system-administration/users | 401 | 403 |

PermitAll không có nghĩa bỏ validation: nếu client chủ động gửi Bearer token sai,
refresh token hoặc access token đã revoke, JWT filter phải từ chối. Webhook IPN
là ngoại lệ không dùng JWT nhưng vẫn cần chữ ký; không đưa callback vào bảng
USER 200/403 hoặc gọi nó để giả tạo thành công payment.

## Chạy lại

PowerShell, gốc repository; đặt JAVA_HOME theo JDK 21 thực tế. Không đổi global
environment và không lấy credential từ production.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12.1'
.\mvnw.cmd --batch-mode '-Dtest=DemoUserAuthorizationMatrixTests' test
.\mvnw.cmd --batch-mode clean verify
git diff --check
```

Focused test dùng profile test/H2, test transaction rollback; full verify còn
chạy MySQL/MinIO Testcontainers. Không chạy lệnh đó trên EC2 production.

## Kết quả và giới hạn

Ngày 23/10/2026, focused test đầu tiên đạt **19 test**, 0 failure/error/skipped.
Lượt full verify đầu tiên chạy 556 test và bị chặn bởi một architecture test:
class test đặt ở `com.shop.order` không được gọi repository. Đã chuyển test
về `com.shop.order.internal.service`, giữ nguyên ArchUnit và production code.
Lượt cuối `spotless:apply clean verify` kết thúc lúc 08:39:20 UTC+7, đạt
**556 test**, 0 failure/error/skipped, gồm cả 19 ca ma trận trên bản cuối,
MySQL 8.0.46/MinIO Testcontainers và kiểm tra ranh giới module. Spotless,
JaCoCo gate và `git diff --check` đạt. Line coverage 91,17%, branch 70,27%;
vẫn chưa đạt mục tiêu toàn dự án >80% branch. Baseline của nhánh này là
`c54c1b3`, chưa chứa 133 test mới của hai PR VNPay; không cộng hai lần hay
coi 556 là tổng sau tích hợp. Không lấy 555 ca qua trong lượt lỗi đầu làm
bằng chứng full gate đã đạt.
Snapshot AWS lúc 08:06 ở trạng thái nghỉ; không coi đó là trạng thái hiện tại.
Chưa có live USER200/admin403/IDOR evidence,
chưa đăng ký hai USER trên web hoặc deploy/bật payment. Trạng thái xuất bản
source được cập nhật riêng bên dưới, không lấy local làm bằng chứng live.

### Kiểm thử bản tích hợp trước PR gate

Sau khi [PR #43](https://github.com/triramisu/shop/pull/43) merge `ca37e4c`
(CI main xanh, CD skipped) và [PR #44](https://github.com/triramisu/shop/pull/44)
merge `d599416` (CI main xanh, CD skipped), nhánh USER được đồng bộ bằng merge
commit, không rebase/force-push. Diff so với main vẫn chỉ ba file test/docs của
task này. Source production/test không đổi trong lúc đồng bộ merge commit B.

Full `clean verify` trên source kết hợp kết thúc **08:51:25 UTC+7 ngày
23/10/2026**, đạt **708 test**, 0 failure/error/skipped; H2, MySQL 8.0.46,
MinIO, Modulith/ArchUnit, Flyway V26 fresh/upgrade, Spotless và JaCoCo đạt.
Line 91,73%, branch 72,36%; >80% vẫn chưa đạt. Compose local `--profile app`,
prod với env example và diff check đều đạt. Đây là tổng thực tế từ Surefire,
không cộng cơ học hai lượt test để tạo số liệu.

[PR #45](https://github.com/triramisu/shop/pull/45) đã mở; sau cập nhật HEAD
phải chờ lại `verify` và `dependency-review`, sau merge kiểm CI của merge SHA.
Theo dõi kết quả cuối tại PR/Actions; không coi việc đã push hoặc lượt CI của
baseline cũ là nghiệm thu HEAD mới. M5 và live USER/SIT vẫn mở.

Hậu kiểm xuất bản: PR #45 đạt hai check trên `eed3d6d`, merge tại `02da697`;
[CI merge commit](https://github.com/triramisu/shop/actions/runs/39887323114)
đã xanh. `git diff eed3d6d origin/main` rỗng tại merge SHA đó, không mất thay đổi.
Phần hướng dẫn chờ gate phía trên là quy trình, không phải trạng thái pending
mới nhất. Sau khi người dùng tự bật lại lab, GET UI/catalog đạt 200 và anonymous
my-info 401 lúc 09:00 UTC+7; chưa dùng USER thật để suy ra ma trận live đã đạt.

## Quy trình GitHub đã dùng để xuất bản an toàn

Thao tác ngày 23/10/2026 dưới đây là xuất bản source, không phải lệnh deploy:

1. Kiểm tra `git status`, giữ mọi file AWS/secret/skill ngoài task; làm trong
   worktree riêng từ main. Viết contract, test, README và chạy gate trước push.
   Chỉ stage đúng file của nhiệm vụ, không `git add .` ở checkout đang dirty.
2. Push nhánh task bình thường; mở PR vào `main`. Với PR phụ thuộc, merge A
   trước, đổi base B về `main`, merge base vào nhánh đã publish và chạy lại CI;
   không force-push hoặc coi check của HEAD cũ là check bản mới.
3. Kiểm tra Settings → Secrets and variables → Actions → Variables: backend
   chưa có repository/environment variables bật `CD_ENABLED`/`DEPLOY_ENABLED`.
   Đối chiếu `.github/workflows/cd.yml`: không tự thêm biến hoặc credential.
4. Chủ repo đã duyệt khôi phục bảo vệ main. Settings → Branches → Add classic
   branch protection rule, pattern đúng `main`: bật Require a pull request,
   Require status checks, chọn `verify` và `dependency-review` (nguồn GitHub
   Actions), bật Require branches to be up to date và Do not allow bypassing.
   Giữ Allow force pushes/Allow deletions tắt. Repo cá nhân không bắt approval
   thứ hai; không thêm rule signed commit/merge queue/code owner ngoài phạm vi.
5. Create/Save; nếu GitHub hiện Confirm access, chủ tài khoản tự xác minh email,
   không gửi mã cho người vận hành. Sau đó mở lại rule để kiểm đủ các checkbox;
   API `branches/main` phải báo protected=true, enforcement_level=everyone và
   đúng hai context. Không chỉ dựa vào việc đã bấm Save.
6. Chờ `verify` và `dependency-review` thành công trên đúng HEAD/base; xử lý
   conflict hoặc lỗi thật trước merge. Không dùng quyền admin để bỏ qua gate.
   Merge commit giữ lịch sử cho chuỗi PR A/B. Sau merge kiểm workflow CI của
   chính merge SHA trên `main`; dependency-review có thể skipped ở push vì
   workflow chỉ chạy job đó cho pull_request. Backend CD phải vẫn skipped.
7. Chỉ đồng bộ local main/cleanup nhánh khi main CI xanh và không còn PR phụ
   thuộc. Không ép checkout hoặc xóa worktree dirty. README phải phân biệt
   local gate, PR CI, merge CI, image build và deploy/live test; không suy ra
   bước sau đã đạt từ bước trước. Lưu báo cáo chi phí tài khoản ở local riêng.

GitHub connector từng trả 403 khi tạo PR; đã dùng phiên trình duyệt đúng chủ
repo, không mở rộng quyền app hoặc trích xuất token/cookie. Quy trình bảo vệ
không đổi IAM, AWS, dữ liệu, payment hoặc yêu cầu thêm dịch vụ trả phí.
