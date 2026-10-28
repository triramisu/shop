# M5.6A — VNPay sandbox status query

[Mục lục tài liệu](README.md) · [Triển khai](../deploy/README.md)

## 1. Mục tiêu và phạm vi

Thêm outbound adapter `querydr` cho Payment, để một use case đối soát phía server
có thể hỏi trạng thái giao dịch khi mất IPN. Actor là backend, không phải browser.
Ưu tiên VNPay theo yêu cầu hiện tại. Task này không có scheduler, endpoint public,
refund, mutation Payment/Order/Inventory, migration, UI hoặc thay đổi AWS.

## 2. Ownership và tiền đề

Payment sở hữu port truy vấn và adapter trong vertical slice VNPay. Dùng lại
merchant config/signature và reference/amount/date của checkout M5.7A. Input phải
được lấy từ payment attempt đã lưu (bao gồm `createdAt` gốc), không lấy trạng thái,
giá hoặc reference từ client. Port không phụ thuộc repository/entity.

## 3. Happy path và bất biến

POST JSON đến sandbox cố định; ký HMAC-SHA512 trên chuỗi phân tách bằng `|`
(khác canonical URL của checkout). Mỗi lần gọi có request ID mới, transaction date
là create date của checkout gốc, create date của query là thời điểm hiện tại GMT+7.
Chỉ nhận JSON có chữ ký hợp lệ, đúng merchant, reference, amount, order info và
transaction type `01`. Response code `00` chỉ nói truy vấn thành công, không chứng
minh đã thu tiền. Transaction status `00` → SUCCEEDED, `01` → PENDING, `02` → FAILED;
các trạng thái khác → UNKNOWN. Response lỗi/không tìm thấy/không khớp không được
biến thành một kết quả thanh toán thành công hoặc thất bại giả.

## 4. Security và vận hành

Mặc định tắt; chỉ tạo bean khi provider là `vnpay` và query được bật. Secret chỉ
đọc từ env, không log payload/PII/signature; error code hữu hạn, được làm sạch.
Response xác minh bằng current key hoặc tối đa hai previous keys, so sánh constant
time. Chặn pipe/control character trong trường ký, duplicate JSON key, trailing
JSON, scalar sai kiểu và response quá 16 KiB. Endpoint runtime không tùy biến,
HTTPS, không redirect, không retry tự động. Timeout connect/read/call mặc định
2s/3s/5s, tối đa 10s; tối đa hai lời gọi đồng thời mỗi instance/JVM, không xếp hàng. Circuit breaker
riêng: window 10, minimum 5, failure threshold 50%, open 30s, half-open 2. Metrics
không chứa transaction ID. Không giữ DB transaction khi gọi mạng. Server IP là
IPv4 outbound do operator cấu hình, không tin header từ browser.

## 5. Đầu ra

Port/request/result, query codec, HTTP gateway, resilience/config, config env
mặc định off, test contract/HTTP/signature/config/resilience và hướng dẫn này.
Không thêm dependency hoặc schema.

## 6. Trường hợp nghiệm thu

Kiểm tra request golden vector độc lập; chữ ký response/rotation; success, pending,
failure, unknown; sai merchant/ref/amount/order/type; lỗi query và hash; malformed,
duplicate/trailing/oversized/non-JSON; HTTP 4xx/5xx, redirect, timeout; breaker
open/half-open và concurrency; config sai fail-fast khi bật, không yêu cầu query
config khi tắt. Regression checkout/IPN và module boundaries phải vẫn xanh.

Request body được đánh dấu one-shot để ngăn cả HTTP follow-up `503` với
`Retry-After: 0` (chỉ tắt connection retry chưa đủ trong OkHttp 5.3.2). Giới hạn
16 KiB áp dụng cho dữ liệu sau giải nén, không chỉ `Content-Length` trên đường
truyền. Test riêng phải chứng minh hai hành vi này.

## 7. Quality gate và bằng chứng

Chạy focused test, Spotless, `git diff --check`, rồi `./mvnw.cmd --batch-mode verify`
với JDK 21 và Docker cho MySQL/MinIO. Ghi kết quả thật sau khi chạy; HTTP giả lập
không thay thế SIT với VNPay. Không tuyên bố M5.6/M5 hoặc branch coverage >80%
đã hoàn tất chỉ từ task adapter này.

## 8. Deploy, rollback và giới hạn

`VNPAY_QUERY_ENABLED=false` giữ nguyên hành vi checkout/IPN. Khi bật cần thêm
`VNPAY_QUERY_SERVER_IP` (IPv4 egress/NAT mà VNPay nhìn thấy), cùng merchant sandbox
đang có. Chỉ cung cấp port: không có caller tự chạy hoặc test giao dịch thật.
Các biến được nối qua `application.yml`; file env example chỉ là mẫu, không tự
nạp vào Java/IntelliJ. Nếu chạy Java trực tiếp phải khai báo environment trong
Run Configuration. AWS prod hiện ép query tắt bằng Docker flags trong template;
muốn bật cần một task review/rollout riêng, không chỉ sửa secret. Bộ Compose
single-host đã bỏ ngày 27/10/2026; cấu hình triển khai xem [AWS prod](../deploy/aws/README.md).
Rollback bằng tắt flag hoặc quay về artifact cũ, không rollback dữ liệu/schema.
Không đụng các thay đổi AWS đang dở trong checkout `D:/shop`.

VNPay query response không echo request ID/date đã ký của request; không coi
response này là bằng chứng freshness đủ để tự động áp dụng state. Bước M5.6B cần
hợp đồng riêng cho lease, retry/backoff, deadline, race với IPN và inbox/audit
trước khi được phép cập nhật trạng thái. Public HTTPS và SIT vẫn chờ triển khai.

Nguồn protocol: [VNPay querydr/refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## Kết quả

Nghiệm thu source local ngày **20/10/2026**, branch `feature/payment/vnpay-query`:

- Focused `-Dtest=VnpayQuery*Tests,VnpayStatusQueryProviderTests test`: **90 test**,
  0 failure/error/skipped. Golden vectors tính độc lập bằng Node crypto cho cả
  request và response UTF-8; HTTP server chỉ chạy loopback, không gọi VNPay.
- JDK **21.0.12.1**, `mvnw.cmd --batch-mode clean verify`: **627 test**, 0
  failure/error/skipped, `BUILD SUCCESS` lúc 11:17:50 UTC+7. Gồm Spring
  Modulith/ArchUnit, Flyway fresh/upgrade, MySQL **8.0.46**, MinIO Testcontainers,
  refund regression và 8 duplicate IPN cạnh tranh thật trên MySQL. Lượt 617 test
  trước đó không có test lỗi nhưng dừng ở format các ca vừa bổ sung; đã sửa format
  rồi chạy lại toàn bộ trên source cuối, không bỏ qua gate.
- Backend: instruction **91,71%**, line **91,48%**, branch **71,18%**
  (2.171/3.050 branch). Mục tiêu toàn dự án **trên 80% chưa đạt**. Riêng package
  `com.shop.payment.internal.provider.vnpay.query`: instruction/line **100%**,
  branch **96,08%** (98/102); không dùng coverage package này thay cho backend.
- Spotless, JaCoCo check, `git diff --check`, Compose local `--profile app` và
  `deploy/compose.prod.yml` với env example đều đạt. Không đổi quality threshold,
  dependency, public REST contract, migration hoặc hai Spring profile dev/prod.
- JAR được tạo tại `target/shop-0.0.1-SNAPSHOT.jar`. Không build/push Docker image
  mới hoặc đổi image đang chạy; không commit/push/merge/CI, deploy hay SIT/live query.
  Sonar/security scan đóng milestone chưa chạy; M5.6/M5 vẫn mở.

Thay đổi chỉ nằm trong worktree của chat
`C:/Users/minhp/.codex/worktrees/c4ff/shop`; checkout `D:/shop` cùng các file AWS
và skill đang staged/dirty từ trước được giữ nguyên. Không lưu merchant key thật,
raw response hoặc runtime log vào source. Tắt query flag là rollback vận hành;
artifact cũ vẫn dùng được vì không đổi schema/API.

Nhiệm vụ kế tiếp là **M5.6B — worker đối soát**, với contract riêng cho lease,
rate limit giữa nhiều instance, retry/backoff/deadline, kết quả cũ, cạnh tranh IPN,
inbox/audit/event và test MySQL trước khi cho phép cập nhật trạng thái. Chưa bắt
đầu worker hoặc coi adapter đã nghiệm thu local là thanh toán sandbox end-to-end.

Cập nhật trong lượt B ngày 20/10/2026: A được lưu thành commit local `9066cf5`,
chưa push/merge. [M5.6B](vnpay-reconciliation.md) bổ sung worker và metadata
request/receipt cho contract query; các mô tả “chưa commit/chưa bắt đầu worker” ở
biên bản A phía trên là trạng thái tại lần nghiệm thu A, không phải trạng thái mới nhất.

Cập nhật ngày 23/10/2026: A đã push và [PR #43](https://github.com/triramisu/shop/pull/43)
đạt `verify`/`dependency-review`, merge vào `main` tại `ca37e4c`; CI của merge
commit đã xanh, backend CD skipped. B đã merge [PR #44](https://github.com/triramisu/shop/pull/44)
tại `d599416`, CI main xanh theo [biên bản mới](vnpay-reconciliation.md). Không bật flag,
deploy hoặc gọi VNPay từ việc merge source; các hạn chế SIT phía trên vẫn còn.
