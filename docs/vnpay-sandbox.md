# M5.7A — VNPay sandbox checkout và IPN

## Hợp đồng nhiệm vụ

1. **Phạm vi:** ưu tiên adapter VNPay sandbox theo yêu cầu merchant mới, trước refund/reconciliation còn lại. Khách lấy action URL từ đơn hàng, mở hosted checkout; VNPay gọi IPN để cập nhật Payment và event Order/Inventory hiện hành. Không thanh toán thật, không refund/query/expiry scheduler, không CI/CD hoặc triển khai AWS.
2. **Ownership/contract:** Payment sở hữu adapter, cấu hình, callback và inbox hiện có (V25 là migration mới nhất, không đổi schema). Giữ nguyên public provider port. Action URL là handoff HTTPS có token HMAC theo attempt; endpoint đọc attempt và tạo URL VNPay từ số tiền server, thời hạn từ createdAt. IP lấy từ remote socket, không tin X-Forwarded-For tự do. Không thêm dependency SDK.
3. **Luồng/invariant:** chỉ VND nguyên đồng, amount nhân 100 và giới hạn 12 chữ số; reference là UUID attempt không dấu gạch. Handoff hết hạn không gia hạn khi refresh. IPN kiểm tra chữ ký trước truy cập DB, merchant/reference/amount/currency/order info; chỉ success khi cả ResponseCode/TransactionStatus bằng 00. Return không thay đổi DB.
4. **An toàn/vận hành:** secret chỉ qua env, không log query/token/raw callback. Chỉ sandbox endpoint cố định; public base URL HTTPS + host allowlist. Handoff token không cấp quyền đọc đơn hàng. GET IPN public nhưng HMAC, query bounded, không duplicate parameters; transaction khóa attempt rồi inbox/event commit cùng nhau. Callback lặp không phát event lần hai. Không có outbound API call nên không thêm retry/circuit breaker giả. IPN trễ hợp lệ vẫn được ghi nhận; mất callback để pending chờ đối soát, tuyệt đối không giả success hoặc tự release theo phỏng đoán.
5. **Đầu ra:** properties/validator, codec chữ ký, hosted adapter/handoff, IPN receipt theo đúng RspCode/Message (ngoại lệ có chủ đích của ApiResponse), OpenAPI/security, config env/Compose, tests và runbook. Bổ sung GET `/api/orders/{orderId}/payment` kiểm tra owner trong Order rồi đọc qua public Payment processing query port; code hiện hành chưa có đường lấy action URL cho khách. Không đổi UI trong task backend này. Giữ fake dev/none prod mặc định, Stripe tùy chọn.
6. **Ca kiểm thử:** encoding/signature độc lập, amount/currency, URL/secret invalid, token tamper/expiry, IP spoof, IPN sai chữ ký/merchant/amount/reference, failure/cancel, duplicate/concurrent/terminal, Return giả success không mutation, DB rollback/event, fake/Stripe regression.
7. **Nghiệm thu:** Spotless, targeted tests, full Maven verify, Modulith/ArchUnit, git diff --check; MySQL concurrency khi Docker khả dụng; build JAR và image nếu Engine chạy. Sandbox SIT và IPN từ VNPay chỉ được ghi đạt sau khi có HTTPS/register URL; không coi test giả lập là giao dịch VNPay thật.
8. **Rollout/rollback:** cấu hình opt-in provider=vnpay, refund vẫn false; không migration. Không đổi provider/secret khi còn giao dịch pending mà chưa xử lý/đối soát; giữ callback hoạt động khi ngừng nhận thanh toán mới. Không downgrade code mất endpoint trước khi xử lý callback còn lại. Không commit credential merchant. Không tuyên bố đóng M5.7/M5 hoặc đạt branch coverage >80% khi chưa có bằng chứng.

## Cấu hình và bước triển khai

Dev mặc định vẫn `fake`, prod mặc định `none`; không yêu cầu Stripe secret. Bật sandbox có chủ đích bằng env riêng ngoài Git, không copy credential vào Dockerfile/image hoặc file example:

```dotenv
PAYMENT_PROVIDER_TYPE=vnpay
PAYMENT_REFUND_ENABLED=false
VNPAY_TMN_CODE=<merchant sandbox của bạn>
VNPAY_HASH_SECRET=<secret sandbox do VNPay cấp>
VNPAY_PUBLIC_BASE_URL=https://api.example.com
VNPAY_ALLOWED_HOSTS=api.example.com
VNPAY_CHECKOUT_TTL=10m
VNPAY_BANK_CODE=
```

`VNPAY_PUBLIC_BASE_URL` là HTTPS backend public origin, không phải S3/frontend, không có path hoặc dấu `/` cuối. Ví dụ trên chỉ là placeholder. Chưa có DNS thì để provider=fake (dev) hoặc none (prod); vẫn build được cùng artifact. Các biến trong file `.env` không tự được Spring đọc: Compose phải truyền `environment` (đã thêm vào compose prod/lab), hoặc shell/IDE phải nạp environment. JAR không chứa merchant secret.

Luồng API:

1. Checkout hiện hành tạo đơn/reserve/auto-initiate payment. Khách gọi `GET /api/orders/{orderId}/payment` với Bearer token, chỉ đọc đơn của mình. Chưa có attempt trả 404/1407: frontend có thể thử lại có giới hạn. API không tạo giao dịch mới mỗi lần đọc và trả `private, no-store`.
2. Mở `result.actionUrl` bằng browser. Handoff token xác thực attempt, backend lấy IP socket + amount server để redirect 303 tới sandbox VNPay. Thời hạn 10 phút tính từ attempt.createdAt, không gia hạn khi refresh. QR là hosted VNPay sinh, không phải QR ngân hàng tĩnh; bank code trống cho khách lựa chọn, `VNPAYQR` nếu muốn chỉ QR.
3. Browser quay về `/api/payments/checkout/vnpay/return`: trả JSON xác minh chữ ký, luôn `paymentConfirmedByBrowser=false`. Frontend đẹp/điều hướng trang kết quả là task UI riêng; hiện khách đọc trạng thái qua API đơn đã xác thực. Không tin responseCode trong thanh địa chỉ.
4. Đăng ký với VNPay URL `https://<backend-host>/api/payments/webhooks/vnpay`. Đây là GET server-to-server, cần HTTPS public và không bị JWT/CAPTCHA/geo/IP allowlist của browser chặn. CDN/reverse proxy phải forward nguyên query và bypass cache với callback/return/handoff/order payment; không log query string/cookie/token. Không đăng ký URL localhost, S3 hoặc ALB HTTP.
5. IPN hợp lệ cập nhật Payment và phát event cho consumer Order/Inventory hiện có. Chỉ success khi cả hai mã bằng 00. Mã 07/99 hoặc kết quả chưa chắc chắn trả 99 và giữ pending, không giả failure; cần operator kiểm tra Merchant Admin/đối soát. Mất IPN, expiry job, query/reconciliation M5.6 và refund VNPay chưa được triển khai trong task này.

Triển khai sau ALB/CloudFront: code mặc định không tự tin X-Forwarded-For, dùng `request.remoteAddr`. Trước smoke thật phải cấu hình trusted proxy tại container/proxy để remoteAddr là IP khách hợp lệ; chỉ tin proxy đã được giới hạn bằng security group/range, không bật xử lý forwarded header cho mọi IP. Chưa kiểm chứng topology của bạn nên không tự thêm trusted wildcard. Tách quyền truy cập browser với server IPN; allowlist chỉ IP của bạn có thể làm VNPay không gọi được.

Giao thức VNPay không có event ID và timestamp chữ ký riêng như Stripe: event key tổng hợp từ TxnRef/TransactionNo/response/status, fingerprint canonical không lưu raw query. Khóa attempt trên MySQL rồi kiểm tra inbox chống lặp; không áp cửa sổ chữ ký 5 phút làm mất IPN retry trễ. Timestamp nhận và PayDate (nếu có) lưu inbox; `signature_timestamp` là PayDate/receivedAt theo mapping legacy, KHÔNG phải bằng chứng chữ ký có timestamp độc lập. Reference bất biến của Payment là TxnRef; mã giao dịch VNPay cuối cùng lưu `provider_object_id` trong inbox cho task refund/query sau. Chỉ chuyển FAILED khi response code thuộc nhóm từ chối xác định và transactionStatus=02; các trạng thái đảo/hoàn tiền/chưa hoàn tất không được diễn giải thành charge thất bại.

Rủi ro còn lại trước go-live: cần task expiry/reconciliation phối hợp deadline giữ hàng của Order/Inventory và payment (bao gồm recovery khởi tạo trễ, đơn hủy hoặc giữ hàng hết hạn trước callback). Task này chỉ sandbox, không bật tiền thật; callback success không được hiểu là hàng đã giao thành công nếu consumer Order đang cần manual recovery. Transactional outbox M6.1 vẫn còn trong roadmap, không tuyên bố bảo đảm event delivery end-to-end từ kiểm thử local.

Không đổi provider/secret tùy tiện khi còn pending. `VNPAY_HASH_SECRET` ký giao dịch mới; `VNPAY_PREVIOUS_HASH_SECRETS` nhận tối đa hai khóa cũ (phân tách dấu phẩy) cho IPN/handoff trong cửa sổ chuyển khóa đã thống nhất với VNPay. Drain/đối soát giao dịch cũ trước khi bỏ khóa cũ; không giữ khóa bị lộ lâu hơn cần thiết hoặc mất endpoint IPN provider cũ. Không lấy một JAR build thành công để tuyên bố đã nghiệm thu cổng thanh toán thật.

## Đóng gói thủ công

Trên PowerShell tại `D:\shop`, đặt JAVA_HOME trỏ đúng JDK 21 nếu máy chưa có, rồi:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1' # Đường dẫn JDK21 đã kiểm tra trên máy hiện tại.
.\mvnw.cmd --batch-mode clean verify
docker build -t t1r4m15u/shop:0.0.1-vnpay-sandbox .
```

Tag trên là gói sandbox local có chủ đích, không tự tăng version/push Docker Hub. Không dùng `-DskipTests` để che gate chưa đạt. Dockerfile vẫn dùng build stage, user 10001, runtime Java21 và healthcheck; `.dockerignore` loại .env/data/Git. Nếu Docker không chạy chỉ có thể build JAR và kiểm thử không-container, chưa thể xác nhận image hoặc concurrency MySQL.

Chưa có public HTTPS nên chưa đăng ký/test IPN thực tế. Khi có URL, điền env bên ngoài source, deploy rồi chạy SIT của VNPay: thành công/hủy/sai amount/chữ ký/retry duplicate, kiểm tra DB và Order/Inventory, cùng callback nhận được thực tế. Không gửi secret/OTP/mật khẩu Merchant Admin vào chat.

## Bằng chứng local — 19/10/2026

### Lượt đầu — Docker Engine chưa hoạt động

- `mvnw.cmd --batch-mode spotless:apply clean verify '-Dtest=*,!*MySql*,!MinioObjectStorageIntegrationTests'`: BUILD SUCCESS, 499 test được chọn, 498 đạt, 1 `StripeSandboxSmokeIT` bỏ qua vì opt-in, 0 failure/error. Bao gồm 59 test mới không-container cho provider/signature, IPN, rollback, owner API; không phải full regression vì 13 lớp Testcontainers bị loại có chủ đích do Engine lỗi. Không thêm skip mặc định hoặc hạ quality gate trong pom/test.
- JaCoCo của lượt trên: line 6287/7057 = 89,09%; branch 2017/2930 = 68,84%. Đạt ngưỡng pom hiện hành nhưng chưa đạt mục tiêu cuối dự án branch >80%; không so trực tiếp với lần full gate đã có MySQL/MinIO.
- Có JAR executable `D:\shop\target\shop-0.0.1-SNAPSHOT.jar`, đã repackage Spring Boot. Dockerfile không đổi, chưa có image mới và chưa push Docker Hub.
- Compose prod/lab `config --quiet` với các file example và `git diff --check`: đạt. Không chỉnh workflow CI/CD hoặc migration cũ; schema vẫn V25.
- Đã thử test `VnpayIpnMySqlConcurrencyTests` (8 callback đồng thời, expected một transition/inbox) nhưng lỗi `Could not find a valid Docker environment` trước khi chạy scenario. Docker Desktop startup log báo `initializing Inference manager ... dockerInference ... The file cannot be accessed by the system`. Không reset factory defaults/xóa Docker state hoặc dữ liệu để né lỗi.
- Chưa có merchant secret thật trong source/test/image, chưa tạo env thật trong repo, chưa gọi cổng để tạo giao dịch thực tế. VNPay SIT/IPN/HTTP runtime trên stack MySQL và UI payment integration chưa nghiệm thu. Không đóng task/milestone từ test ký giả lập.

### Kiểm tra lại — Docker Engine đã hoạt động

- Docker Linux Engine `29.6.1`, Compose `5.2.0`; không reset hoặc xóa dữ liệu Docker hiện có.
- `mvnw.cmd --batch-mode clean verify` **không exclusion**: BUILD SUCCESS, **537 test**, 0 failure/error/skip. Bao gồm 13 lớp test container, MySQL 8.0.46 fresh/upgrade đến V25, MinIO, Modulith/ArchUnit, OpenAPI/security và Spotless. Gate mặc định không chạy `StripeSandboxSmokeIT` opt-in hoặc SIT VNPay thực tế.
- `VnpayIpnMySqlConcurrencyTests` đã đạt: 8 callback đồng thời chỉ tạo một transition và một inbox record; không còn blocker Docker cho kiểm thử này.
- JaCoCo full gate: line 6434/7057 = **91,17%**; branch 2057/2930 = **70,20%**. Mục tiêu cuối dự án branch >80% vẫn chưa đạt, không hạ gate để báo hoàn thành.
- `docker build --progress=plain -t t1r4m15u/shop:0.0.1-vnpay-sandbox .` đạt từ Dockerfile hiện hành. Image local `sha256:a4053af05dc3c6ce041019ae3d4d2081245c9559c376c05695f861448c561a8a`, linux/amd64, user `10001`, 165706713 byte. Build stage skipTests như trước vì full gate đã chạy riêng trước đó; không coi skipTests trong Docker là bằng chứng test. Chưa push Docker Hub; image được build từ working tree hiện tại chưa commit.
- Runtime smoke dùng Compose prod + override giới hạn app 768 MiB/1 CPU, MySQL 512 MiB/1 CPU, MinIO 256 MiB/0,5 CPU; project/volume riêng và port loopback tự cấp. Env test đặt ngoài Git, chỉ dùng merchant giả `TEST1234`, không dùng secret thật hoặc gọi VNPay. App chạy profile `prod`, provider `vnpay`, không cần Stripe key; health `UP`, read-only root filesystem, user 10001, restart count 0.
- HTTP runtime: IPN thiếu chữ ký trả `97`; IPN ký đúng nhưng reference không tồn tại trả `01`; return xác minh chữ ký nhưng `paymentConfirmedByBrowser=false`; API order payment vô danh trả 401. Query DB bằng runtime DML account xác nhận schema V25, 0 payment/inbox row sau các callback bị từ chối. Đây là kiểm thử giả lập trong stack Docker, KHÔNG phải IPN do VNPay gửi.
- Phản hồi health có Content-Type `application/vnd.spring-boot.actuator.v3+json`; harness PowerShell đọc UTF-8 từ byte content trước khi parse JSON. Lượt đầu harness parse health không đúng đã được chạy lại thành công, không sửa ứng dụng để né assertion.
- Đã dọn container/network/volume/env test tạm sau kiểm tra; hai container `shop-mysql`, `shop-minio` cũ giữ nguyên ID và healthy. Giữ image mới và JAR để bàn giao. Không thay đổi workflow, schema, credential thật, AWS hoặc remote Git.

Điểm tiếp tục: có HTTPS backend thì triển khai bằng env riêng, đăng ký IPN và chạy SIT VNPay thực tế. WEB-PAY-01 đã nối API link vào UI riêng ở `D:/shop-ui`, gate local/mock ghi tại [frontend payment](frontend-payment.md); JAR/image backend không chứa bundle đó. Docker/MySQL/build image không còn là blocker. Chưa đóng M5.7A/M5.7/M5 trước khi đủ nghiệm thu bên ngoài. Không tự chuyển sang refund/reconciliation, thay đổi AWS hoặc push Git/Docker Hub từ task này.

Nguồn giao thức: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html và bảng transaction status trong https://sandbox.vnpayment.vn/apis/files/VNPAY%20Payment%20Gateway_Techspec%202.1.0-VN.pdf (trang 34/37).
