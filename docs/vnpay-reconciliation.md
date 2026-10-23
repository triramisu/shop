# M5.6B — VNPay reconciliation worker

## 1. Mục tiêu, actor và phạm vi

System job đối soát payment VNPay sandbox REQUIRES_ACTION/PENDING/UNKNOWN bị mất
IPN, dùng adapter M5.6A. Không làm refund, Stripe query, UI, public API, AWS,
thanh toán thật hoặc tự suy diễn thất bại từ timeout/không tìm thấy giao dịch.
Flag worker mặc định off; không bật trên lab DDoS.

## 2. Ownership và phụ thuộc

Payment sở hữu checkpoint/lease, nhịp gọi dùng chung và lịch sử append-only qua
Flyway V26 (MySQL/H2); không sửa V1–V25. JDBC repository giữ metadata job, JPA
PaymentAttempt giữ invariant/status/version. Dùng cùng domain transition và
PaymentLifecycleEventPublisher với IPN; không import Order/Inventory internals.
Nhánh B phụ thuộc commit local A `9066cf5`, chưa merge main; không gom AWS ở D:/shop.

## 3. Luồng và bất biến

Claim trong transaction ngắn: khóa nhịp provider, chọn candidate có tuổi tối thiểu,
khóa payment trước checkpoint; lưu lease UUID/version/count/deadline rồi commit.
Gọi query ngoài transaction. Complete bằng transaction mới, khóa cùng thứ tự,
fence token/expiry/version/status/reference/observation window. IPN đã commit hoặc
worker mất lease thì không áp dụng kết quả cũ. Chỉ query SUCCEEDED/FAILED đã được
adapter xác minh và receipt mới mới transition; PENDING/UNKNOWN chỉ hẹn lại,
không xóa action URL. Terminal không bị hạ cấp. Deadline/max attempts chỉ chuyển
checkpoint sang MANUAL, không đổi payment thành FAILED/EXPIRED hoặc release kho.

## 4. Bảo mật, độ tin cậy và vận hành

Không nhận input từ browser. Thêm query request UUID do lease cấp, response ID và
SHA-256 canonical receipt vào contract nội bộ để chống replay/audit; không lưu
raw body, hash secret hoặc signature. Response ID chỉ unique trong ngày theo
VNPay nên dedup bằng canonical receipt hash, không giả định ID unique vĩnh viễn.
Không có timestamp/request-ID echo được VNPay ký: chỉ chấp nhận lời gọi HTTPS
trực tiếp từ adapter, observation trong lease và tối đa 10s, version chưa đổi;
không tuyên bố đây là bằng chứng freshness mật mã đầy đủ. SIT vẫn là gate trước bật.
JDBC đọc/ghi timestamp bằng Calendar UTC tường minh, đồng bộ cấu hình Hibernate;
không phụ thuộc timezone của Windows/Ubuntu hoặc mặc định driver MySQL.

Lease mặc định 30s, query timeout <=10s; token cũ không ghi đè lease mới. Nhịp DB
chung tối thiểu 5s/provider trên mọi instance; batch tối đa 10 mặc định, không giữ
khóa DB khi gọi mạng. Backoff 1m→30m + jitter có giới hạn, tối đa 12 lần/24h.
Crash sau claim được thu hồi khi lease hết hạn; crash trước commit rollback mọi
transition/receipt. Receipt hash có unique constraint; audit một dòng/lease và
chỉ INSERT. Lỗi item được cô lập; lỗi auth/config không retry mù. Metric tag hữu
hạn, pending age/manual count để cảnh báo, log không chứa payload/cause từ provider.

## 5. Đầu ra

V26: checkpoint, lịch sử và nhịp provider; repository/store claim/complete;
processor/scheduler/properties; query receipt metadata; env mẫu default-off;
test domain/config/HTTP regression/H2/MySQL migration, lease/race và runbook.
Không đổi REST, dependency, hai profile dev/prod hoặc consumer event contract.

## 6. Ca nghiệm thu

Pending→success/failure đúng một transition/event; unknown/network giữ state;
trùng receipt, stale version, IPN thắng race, result sau lease, lease cũ sau
reclaim, hai worker tranh claim, rate limit dùng chung, crash rollback/reclaim,
max attempts/deadline/manual, lỗi permanent và poison item. Test phải chứng minh
outbound không nằm trong transaction và claim đã commit. MySQL quyết định locking.

## 7. Gate

Focused tests + full `mvnw.cmd --batch-mode clean verify` JDK21/Docker;
Flyway fresh và V25→V26 bảo toàn payment; constraints/indexes, Modulith/ArchUnit,
Spotless, Compose local/prod và diff check. Báo coverage thật, không hạ threshold.
Không gọi VNPay thật, deploy, push hoặc coi M5/M5.6 đã đóng từ gate local.

## 8. Rollout, rollback và giới hạn

Giữ `PAYMENT_RECONCILIATION_ENABLED=false`. Khi được nghiệm thu SIT mới bật cùng
provider=vnpay, query enabled và egress IP đúng; bắt đầu batch nhỏ. Tắt worker để
dừng claim mới, giữ bảng/history/uncertain state, chờ lease cũ hết hạn trước rollout.
V26 additive; rollback artifact giữ dữ liệu, không drop bảng hoặc sửa migration cũ.
Trước rollout phải sao lưu, áp V26 bằng credential Flyway DDL riêng và giữ flag
off để kiểm tra. MySQL DDL không đảm bảo rollback nguyên migration; nếu lỗi phải
dừng rollout, kiểm kê schema và roll-forward có kiểm soát, không repair/drop mù.

Gap producer commit→event delivery vẫn thuộc transactional outbox M6.1; lịch sử
lưu event ID để điều tra/replay có kiểm soát nhưng B không tự xây dispatcher thứ
hai. Consumer inbox/recovery hiện có vẫn dùng lại. Refund reconciliation chưa làm.

## Runbook

- Quan sát `shop.payment.reconciliation.items`, oldest-pending age và manual count.
  Cảnh báo khi manual >0 hoặc pending lâu hơn SLO vận hành; điều tra checkpoint,
  journal và payment theo ID trong DB qua quyền operator, không mở MySQL public.
- Timeout/UNKNOWN: giữ checkpoint READY, chờ backoff; không gửi payment mới để bù.
- MANUAL: tắt worker nếu lỗi merchant/IP/outbound, đối chiếu Merchant Admin + IPN.
  Chưa có API manual resolution ở B; không sửa SQL để giả PAID hoặc xóa receipt.
- IPN terminal thắng query: kết quả query chỉ ghi lịch sử stale, không confirm/release lặp.
- Metric manual chỉ đếm payment còn chưa rõ kết quả. Nếu IPN đã xác nhận terminal,
  checkpoint MANUAL cũ vẫn giữ lịch sử nhưng không tiếp tục tạo cảnh báo cần xử lý.
- Đổi flag cần restart application/container; chờ query đang chạy kết thúc hoặc
  lease hết hạn. Rollback/tắt flag không đồng nghĩa hủy payment; tiếp tục nhận IPN.

Tra cứu read-only (không phải API manual resolution):

```sql
SELECT HEX(c.payment_attempt_id) AS payment_id, p.status AS payment_status,
       c.state AS checkpoint_state, c.query_count, c.last_error,
       c.next_query_at, c.lease_until, c.deadline_at
FROM thanh_toan_doi_soat c
JOIN thanh_toan_lan_thu p ON p.id = c.payment_attempt_id
ORDER BY c.updated_at DESC
LIMIT 20;
```

Payment SUCCEEDED đến muộn vẫn là bằng chứng đã thu tiền; nếu tồn kho đã hết hạn,
Order có thể cần xử lý thủ công theo pipeline M5.5A. Không sửa Order thành PAID
hoặc tự phát một khoản hoàn tiền ngoài capability/refund workflow đã nghiệm thu.

Query ID chỉ dùng cho một lease/lời gọi; retry phải tạo lease và request ID mới.
Không tái sử dụng request từ lần gọi cũ để vượt backoff hoặc duplicate guard.

## Kết quả

Gate source local ngày **20/10/2026**, branch `feature/payment/vnpay-reconciliation`:

- `mvnw.cmd --batch-mode spotless:apply clean verify` với JDK **21.0.12.1**:
  **689 test**, 0 failure/error/skipped, `BUILD SUCCESS` lúc **17:01:09 UTC+7**.
  Có **62 test mới** (15 H2 integration, 16 MySQL integration/migration, 31 unit)
  ngoài regression query/checkout/IPN/refund, Modulith/ArchUnit và toàn bộ module.
- MySQL **8.0.46**: V25→V26 bảo toàn captured payment/amount/UTC creation time;
  Flyway fresh/upgrade, Hibernate validate và constraints lease/count đạt. Hai
  worker tranh claim, hai completion tranh một lease chỉ có một event/receipt;
  IPN đã commit không bị query failure ghi đè. Crash/reclaim, rollback cả
  transition + audit khi publisher lỗi, replay/stale/version/expiry, query ngoài
  transaction với claim đã commit đều đạt. MinIO Testcontainers regression đạt.
- Test bắt được mốc khởi tạo TIMESTAMP không hợp lệ và lệch UTC giữa JDBC/JPA;
  đã sửa timestamp về UTC tường minh. H2 session riêng chuẩn hóa UTC; MySQL vẫn
  chạy trên host Windows UTC+7, không đổi timezone toàn JVM để che lỗi. Fixture
  IPN dùng query parameters đúng cách; không nới xác minh chữ ký production.
- Lượt full 687 test trước đó xanh; sau khi sửa metric manual để bỏ payment đã
  terminal khỏi cảnh báo, thêm hai test và chạy lại full trên bản cuối: 689 xanh.
- Backend instruction **92,00%**, line **91,73%**, branch **72,30%**
  (2.341/3.238). Riêng package reconciliation instruction **97,87%**, line
  **98,59%**, branch **90,00%** (162/180). Mục tiêu backend **trên 80% vẫn chưa đạt**;
  không đổi JaCoCo threshold hoặc loại code khỏi coverage để tăng số liệu.
- Spotless, JaCoCo check, `git diff --check`, Compose local `--profile app` và
  prod với env example đạt. Không đổi dependency, REST/event contract,
  `application-dev.yml`, `application-prod.yml` hoặc checksum V1–V25. Bảy assertion
  số migration được nâng đúng 25→26; không bỏ qua test/constraint cũ.
- JAR mới có tại `target/shop-0.0.1-SNAPSHOT.jar`; không build/push Docker image,
  không gọi merchant thật, deploy, push/merge hoặc xác nhận GitHub CI. Sonar/security
  scan đóng milestone, RDS 8.4/cloud smoke và SIT chưa chạy. Worker/query vẫn off.

Chỉ thay đổi worktree `C:/Users/minhp/.codex/worktrees/c4ff/shop`. A đã được lưu
commit local `9066cf5`; B giữ branch/commit riêng, chưa xuất bản remote. `D:/shop`
cùng các file skill/AWS đang staged hoặc dirty được giữ nguyên.

M5.6/M5 vẫn mở: refund/Stripe reconciliation, producer outbox M6.1, API manual
resolution và HTTPS/SIT chưa thuộc nghiệm thu này. Bước tiếp theo đề xuất là
provider refund theo ưu tiên VNPay, với contract riêng và flag mặc định tắt;
không tự thực hiện hoàn tiền thật hoặc bỏ gate public HTTPS/IPN.

## Xuất bản và kiểm tra lại — 23/10/2026

- Docker Engine local 29.6.1, JDK 21.0.12.1; chạy lại `clean verify` trên B chứa A:
  **689 test**, 0 failure/error/skipped, kết thúc 08:25:59 UTC+7. MySQL 8.0.46,
  MinIO, Modulith/ArchUnit, Flyway fresh/upgrade và JaCoCo đạt; line 91,73%, branch
  72,30%. Compose local `--profile app`, prod với env example và diff check đạt.
- Đã push A/B bằng branch riêng, không force-push. [PR #43](https://github.com/triramisu/shop/pull/43)
  đạt `verify`/`dependency-review`, merge commit `ca37e4c`. [PR #44](https://github.com/triramisu/shop/pull/44)
  đạt hai check trên `2578867`; sau đổi base và cập nhật HEAD phải chờ gate mới,
  không dùng kết quả HEAD cũ để bỏ qua bảo vệ `main`.
- Đồng bộ `origin/main` vào B bằng merge commit (giữ lịch sử A, không rebase
  nhánh đã publish). Tree production/test của lần đồng bộ trùng bản 689 test
  đã kiểm; thay đổi bổ sung chỉ là biên bản. Hậu kiểm CI commit trên `main`
  là gate riêng sau từng merge, không đồng nghĩa deploy.
- GitHub Actions repository/environment variables đều chưa được cấu hình;
  `CD_ENABLED` và `DEPLOY_ENABLED` không bật. Không cấp thêm quyền AWS, gọi
  merchant, chạy SSM hay thay ASG/RDS/S3/DNS/secret; lab vẫn nghỉ.
- `main` được khôi phục protection cho đúng nhánh: PR bắt buộc, hai check
  GitHub Actions, strict up-to-date, không bypass kể cả admin, không force-push
  hoặc xóa. Không yêu cầu approval của người thứ hai trên repo cá nhân.
- Đây chỉ là xuất bản source. V26 chưa chạy trên RDS 8.4; chưa build/push image
  mới, deploy, live USER/SIT hoặc bật query/worker. Không đóng M5.6/M5.
