# M5.5B.1 — Lõi hoàn tiền có kiểm soát

## 1. Mục tiêu, actor, ngoài phạm vi

Actor có capability `PAYMENT_REFUND` yêu cầu hoàn một phần hoặc toàn bộ phần
còn lại của một payment đã SUCCEEDED. USER/STAFF không có quyền này mặc định;
migration chỉ cấp cho ADMIN. B.1 là task độc lập của M5.5B, không đóng task cha.
Ngoài phạm vi: hoàn tiền thật Stripe/VNPay, trả hàng/dispute, UI quản trị,
refund webhook, cập nhật trạng thái Order, scheduler reconciliation và outbox.

## 2. Sở hữu dữ liệu và phụ thuộc

Payment sở hữu budget và refund attempt riêng, không đổi payment state machine.
Flyway V25 additive, FK chỉ trong Payment. API:
`POST /api/admin/payments/{paymentAttemptId}/refunds` với `Idempotency-Key`;
`GET /api/admin/payments/{paymentAttemptId}/refunds/{refundId}`.
Body `{ "amount": 100000, "currency": "VND" }`; bỏ/null amount nghĩa là
hoàn toàn bộ số tiền chưa được giữ hoặc hoàn, tính trong transaction.
Client không được gửi provider/status/actor. API dùng ApiResponse, JWT và quyền
thật; không truy cập repository Order/Inventory/Identity.

## 3. Luồng và bất biến

Khóa payment → kiểm tra key/payload → kiểm tra SUCCEEDED và currency → tạo/đọc
budget → giữ tiền → lưu CREATED, commit → gọi provider ngoài transaction →
khóa payment → ghi kết quả và budget trong transaction mới.
`0 <= succeeded <= allocated <= captured`, allocated gồm thành công và mọi
CREATED/PENDING/UNKNOWN. FAILED mới trả ngân sách; timeout/exception/protocol
không rõ kết quả phải UNKNOWN, không tự suy diễn thất bại.
Mỗi refund là một attempt độc lập, không tạo attempt mới khi retry HTTP.
Full refund replay phải giữ nguyên amount đã tính ở lần đầu.

## 4. Bảo mật, idempotency, concurrency và vận hành

Quyền được kiểm tra ở controller và application service; actor lấy từ phiên
xác thực. Key ASCII 1–100 ký tự, scope payment, phân biệt hoa/thường trên cả
MySQL/H2 bằng SHA-256 key; fingerprint canonical amount/currency/full marker.
Cùng key/cùng payload trả trạng thái hiện tại, khác payload 409, không gọi
provider lần hai kể cả UNKNOWN hoặc crash để CREATED. Không có retry ngầm.
Hai key khác nhau tranh chấp cùng payment được serialize bằng row lock MySQL;
budget có CHECK bảo vệ giới hạn ở DB. Đọc refund sai payment trả cùng 404.
Không log body/key/actor hoặc dữ liệu provider thô; chỉ lưu ID kỹ thuật và mã
lỗi hữu hạn. Không có PAN/CVV/secret. Response `private, no-store`.
`PAYMENT_REFUND_ENABLED` mặc định false, chặn tạo mới nhưng vẫn cho đọc kết quả.
Chỉ fake adapter khi provider FAKE (production guard hiện có cấm FAKE ở prod).
Provider không hỗ trợ phải 503 trước khi giữ tiền, không giả thành công.

## 5. Đầu ra

Entity/budget/repository, transaction service, orchestration, provider refund
port và fake adapter có control deterministic dành test; DTO/mapper/controller,
V25, error/message Việt, OpenAPI, cấu hình và test. B.1 chưa phát event sang
Order để tránh hai nguồn trạng thái chưa có inbox/đối soát.

## 6. Nghiệm thu

Partial→full; từ chối chưa thu, currency mismatch, <=0/scale/precision sai,
vượt ngân sách; replay full/partial; key xung đột; key phân biệt hoa thường;
hai refund cạnh tranh không vượt capture; duplicate không gọi provider trùng;
failure trả hold; pending/timeout/crash giữ hold; đọc sai payment; 401/403,
ADMIN được cấp quyền và STAFF không tự có quyền; feature flag/unsupported
provider không mutation; schema fresh/upgrade từ V24 và constraint MySQL/H2.

## 7. Kiểm chứng

`mvnw.cmd spotless:apply verify` (JAVA_HOME JDK21), test MySQL Testcontainers
8.0.46, Modulith/ArchUnit, OpenAPI, migration fresh/upgrade, full regression,
`git diff --check`; UI `npm run build` cho bước xuất bản riêng. Ghi kết quả thực
tế sau khi chạy, không coi local pass là CI/merge/deploy hoặc branch >80%.

## 8. Rollout, rollback và các phần tiếp theo

Triển khai V25 trước/cùng code, giữ flag false cho tới nghiệm thu provider.
Rollback code không xóa bảng/refund/budget hoặc sửa V25. Sau khi có refund
đang chạy, không rollback sang code không hiểu chúng rồi tạo refund ngoài hệ
thống; dừng mutation và đối soát thủ công. B.2 bổ sung Stripe PaymentIntent /
Charge ID tin cậy, adapter và refund webhook; B.3 event versioned + Order
metadata/inbox. M5.6 scheduler dùng lại transaction boundary của refund.

V25 thêm quyền mặc định cho ADMIN và revoke các family của ADMIN hiện có;
ADMIN phải đăng nhập lại để lấy claim mới. Phiên USER/STAFF không bị revoke
bởi migration này. Không thay đổi snapshot, trạng thái Payment/Order hoặc
tồn kho cũ; 27 migration trước V25 phải giữ nguyên checksum.

### Runbook local và trạng thái không chắc chắn

- Chỉ bật `PAYMENT_REFUND_ENABLED=true` với FAKE trong môi trường dev/test
  cô lập; không coi `SUCCEEDED` fake là giao dịch ngân hàng thật.
- Giữ nguyên key và body của mỗi yêu cầu. Khi timeout/503, nếu API tạo đang
  bật, gửi lại đúng key/body để đọc ID/trạng thái hiện tại, không gọi provider
  lần hai. Khi đã có ID, dùng GET theo payment/refund, kể cả khi tắt tạo mới.
- CREATED/PENDING/UNKNOWN vẫn giữ ngân sách. B.1 chưa có scheduler hay API
  manual resolution; không sửa SQL thành FAILED/SUCCEEDED, không xóa bản ghi
  hoặc dùng key khác để bù một khoản chưa rõ kết quả. B.2/M5.6 sẽ thêm luồng
  xác minh provider trước khi thay đổi trạng thái.
- Migration additive không tự mở refund; flag mặc định false. Nếu cấu hình
  provider Stripe/NONE ở B.1, POST mới trả 503 trước khi giữ tiền.

Stripe hiện chỉ lưu Checkout Session ID; API refund cần Charge/PaymentIntent,
không truyền `cs_*` vào refund API. Nguồn: [Create refund](https://docs.stripe.com/api/refunds/create)
và [Checkout Session](https://docs.stripe.com/api/checkout/sessions/object).

## Bằng chứng

Nghiệm thu local ngày 16/10/2026, branch `codex/payment-refund-core`:

- `spotless:apply clean verify` đạt **459 test, 0 failure/error/skipped**;
  instruction **91,14%**, line **90,96%**, branch **68,99%**. POM/dependency và
  quality gate không đổi. Branch toàn backend chưa đạt mục tiêu trên 80%.
- MySQL 8.0.46: Hibernate `ddl-auto=validate`; migration fresh và V24→V25;
  bảo toàn captured payment; chỉ cấp ADMIN và chỉ revoke phiên ADMIN;
  khóa cạnh tranh 3 vòng, duplicate concurrent, crash/timeout hold, completion
  replay/stale failure, SQL CHECK/unique (lỗi MySQL 3819/1062), rollback budget
  khi insert refund lỗi và key phân biệt hoa thường đều đạt.
- H2 dùng profile test `ddl-auto=none` hiện có, chạy API/schema/upgrade;
  không dùng H2 để kết luận locking hoặc Hibernate type validation của MySQL.
- API qua MockMvc và JWT đăng ký/đăng nhập thật: 401/403, ADMIN, STAFF/USER
  bị từ chối, partial/full/replay, sai tiền/currency/key/UUID, flag tắt, provider
  unsupported và GET sai payment đạt. Spy trên fake provider kiểm tra không có
  transaction đang mở và bản ghi refund đã commit trước outbound call.
- Toàn bộ 27 migration cũ giữ nguyên Git blob/checksum; chỉ thêm V25.
  Ba Compose local/prod/lab `config --quiet` và `git diff --check` đạt.
- JAR `target/shop-0.0.1-SNAPSHOT.jar` đã chạy lại trên 8080: health UP,
  OpenAPI có route refund, anonymous refund trả 401; schema chính `shop` ở V25,
  không có bản ghi refund (không seed fixture vào đây). Flag tạo refund vẫn false.
- Lượt full đầu có 3 assertion Identity cố định 12 quyền. Cập nhật danh sách
  chính xác thành 13 quyền, ADMIN có PAYMENT_REFUND, STAFF/USER không có;
  không nới assertion. Lượt full cuối chạy lại toàn bộ và BUILD SUCCESS.

UI đã xuất bản độc lập tại [triramisu/shop-ui](https://github.com/triramisu/shop-ui),
initial commit `1a03080`; 6 test + TypeScript/Vite build đạt, Git sạch và HEAD
khớp origin/main. Không commit env thật, node_modules/dist hoặc build cache.
Backend chưa commit/push/CI/merge/deploy; layout ROOT-01, WEB-01 và file skill
đang staged từ trước được giữ nguyên, không tự gom vào commit khác nhiệm vụ.
Sonar/security scan milestone và sandbox tiền thật chưa chạy; M5.5B/M5 vẫn mở.

Log gate cuối lưu ngoài Git tại thư mục visualization của task,
`refund-regression-r2.log`. Đây là bằng chứng local, không phải kết quả GitHub CI.
