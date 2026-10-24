# PAY-SIT-01A — Preflight VNPay sandbox qua HTTPS

## Hợp đồng tám phần — 24/10/2026

1. **Phạm vi/actor:** chuẩn bị nghiệm thu VNPay sandbox trên web đang chạy.
   Operator kiểm tra HTTPS/API, stack/fleet/secret và chốt rào chắn trước khi
   bật payment. Không auto-ban, thu tiền thật, refund, đối soát tự động hoặc
   thay DNS/UI. Preflight không được tự Execute hay tạo giao dịch.
2. **Sở hữu/phụ thuộc:** baseline main `847b093`, image RC2/code V26 đã rollout
   được ghi ở nhánh `feature/release/mysql84-local-rc`; purchase local 724 test
   tại `43522e6`, chưa merge/CI. AWS deployed state mới là nguồn sự thật vận
   hành, không dùng file prod dirty tại `D:/shop` để ghi đè mù.
3. **Happy path/invariant:** xác minh đúng tài khoản/region/stack; fleet khỏe,
   image ghim digest; public Catalog hoạt động và private API cần JWT. Kiểm
   cấu hình merchant trong RAM, chỉ xuất boolean/mã lỗi hữu hạn. Cấu hình hiện
   khởi động với provider=none phải được nhận diện, không báo sẵn sàng giả.
4. **An toàn:** AWS CLI dùng phiên ngắn hạn; không dump secret/env/token/body
   request vào output, file hoặc README. Không đổi DB/JWT/IAM/AMI/image hoặc
   bỏ bảo vệ WAF. Credential merchant từng đưa vào chat cần rotation do chủ
   merchant tự thực hiện; không dùng lại từ lịch sử chat để bật public.
5. **Đầu ra:** công cụ preflight read-only + test negative/redaction, runbook
   từng bước và kết quả thật. Nếu đủ điều kiện mới chuẩn bị candidate/change
   set phạm vi hẹp, giữ default none và opt-in sandbox riêng; review/Execute
   cần xác nhận riêng, không nằm trong quyền chạy preflight.
6. **Ca kiểm tra:** sai account/stack, stack đang update/fleet unhealthy,
   secret thiếu/trùng key/JSON thay env-file, URL/host/TTL không hợp lệ,
   refund/query/worker/admin bootstrap bật, thiếu merchant key; không lộ
   giá trị kể cả khi parse lỗi. Không dùng dữ liệu test để sửa secret thật.
7. **Nghiệm thu:** unit test Node độc lập AWS; preflight live qua CLI nếu phiên
   hợp lệ và HTTP smoke nhỏ; ghi rõ pass/blocker, không coi HTTP200 hoặc
   preflight xanh là đã nghiệm thu IPN. Không chạy full Maven lại nếu không
   đổi Java/schema/dependency; giữ bằng chứng purchase ở task trước.
8. **Rollout/rollback:** preflight không có side effect nghiệp vụ. Thay mode
   sau này có thể rollout ASG/tăng phí tạm và phải review riêng. Khi đã có
   payment pending, không rollback bằng cách xóa endpoint/secret cũ làm mất
   IPN retry; cần drain và đối chiếu trước. Không xóa dữ liệu test trên AWS
   khi chưa có phạm vi dọn riêng.

## Kết quả đọc AWS — 24/10/2026, khoảng 09:24 UTC+7

- `aws login --profile shop-prod --region ap-southeast-1` đã thành công sau
  khi chủ tài khoản tự đăng nhập/cho phép CLI. STS xác minh đúng tài khoản
  Shop; không lưu credential vào repo và chưa đổi tài nguyên.
- GET ngày 24/10: UI200, Catalog200/12 sản phẩm, anonymous my-info401,
  IPN404. Đây là smoke không token, không phát sinh giao dịch.
- Đã đối chiếu **Original template đang deploy và Launch Template version 5**:
  cả hai ép `PAYMENT_PROVIDER_TYPE=none`, auto-initiation=false và refund=false.
  Đổi giá trị trong secret một mình không bỏ được CLI override này. Không dùng
  bản YAML dirty ở `D:/shop` để update nguyên khối; bản draft có tham số AMI
  chưa tồn tại trong stack đang chạy.
- Stack `shop-aydibro-prod` UPDATE_COMPLETE/application, image ghim digest;
  ASG min/desired/max=2/2/4, hai app InService/Healthy tại hai AZ, hai target
  ALB Healthy và không refresh đang chạy. Không gọi SSM/đổi runtime để kiểm tra.
- Original template 49.243 byte, SHA-256
  `ce6b948680b10693eb807cac5d42ce2c7caeff506ed42733fb06e7a96642a8cb`.
  Đây là dấu vết bản đã đọc, không phải candidate/payment approval.
- Secret AWSCURRENT đúng env-file; version không đổi giữa các lần đọc.
  Kiểm tra merchant code, hash secret, public base URL và host allowlist
  **chưa đạt** (thiếu hoặc chưa hợp lệ). Không in giá trị/độ dài của key.
  Hai switch event-consumption/recovery cũng chưa đạt yêu cầu explicit false
  của giai đoạn staging. Kết luận này từ config hiện tại, không phải kiểm tra
  environment bên trong container hoặc bằng chứng đã phát sinh payment.
- Preflight live trả `BLOCKED`, `readyForSit=false`; đây là kết quả nghiệp vụ
  được dự kiến, không phải lý do nới gate hoặc tự tạo credential.
- UI local tại `D:/shop-ui/.env.production` có `/api` và allowlist
  `https://aydibro.click`. Chưa đối chiếu bundle S3/CI đang phục vụ; không coi
  file local là bằng chứng deployed UI handoff.
- Chưa bật merchant/payment hoặc đăng ký VNPay IPN; chưa được gọi là SIT đạt.

## Công cụ đọc-only và cách kiểm tra lại

Không cần Python, SDK AWS hoặc package Node bên ngoài. Chạy tại checkout
backend chứa thay đổi này, Node >=20 và AWS CLI đã đăng nhập:

```powershell
node --test ci/aws-vnpay-preflight.test.cjs
node ci/aws-vnpay-preflight.cjs --profile shop-prod --region ap-southeast-1 --stack shop-aydibro-prod --account 233377400170
```

Lệnh thứ hai là **một dòng**, dùng được cả PowerShell và Bash. Không thêm ký
tự `$` của prompt; không dùng backtick PowerShell để xuống dòng trong Bash.
CLI của helper trả 2 khi có blocker, 1 khi lỗi đọc/đầu vào, 0 khi các kiểm tra
metadata/cấu hình đạt. Shell có thể ánh xạ mã lỗi native sang 1; luôn đọc
`status`/`blockers` trong JSON. Cả mã 0 vẫn **không** phải SIT/approval/go-live.

Helper chỉ cho phép STS/CloudFormation/ASG/EC2/ELB describe/get/list và đọc
đúng secret ARN thuộc stack/account/region, đúng version AWSCURRENT. Không
gọi SSM, ghi secret, tạo change set, upload, Execute hoặc gọi API mua hàng.
AWS stdout/stderr được giữ trong tiến trình; không chuyển thông báo lỗi gốc
hoặc nội dung secret ra JSON. JavaScript không bảo đảm zeroize immutable
string: chạy tiến trình ngắn hạn trên máy tin cậy, không debugger/dump RAM
hoặc log toàn bộ process. Không dùng `aws --debug` khi làm việc với secret.

Các kiểm tra cố ý chặt hơn Spring: env-file dùng key uppercase, không `export`,
JSON, BOM, key trùng hoặc biến chỉ có tên; Docker không tự bỏ dấu nháy/expand
`$`. URL khớp chính xác HTTPS website, không `/` cuối, host allowlist một
hostname; TTL chỉ `1m`–`10m` hoặc `60s`–`600s`. Format đúng **không xác minh**
merchant còn hoạt động hoặc key đã được VNPay rotation.

## Bước người dùng làm ngay: chuẩn bị secret, chưa bật payment

1. Đăng nhập merchant sandbox/kênh hỗ trợ VNPay chính thức và yêu cầu đổi
   khóa đã từng đưa vào chat. Không đoán nút rotation trên giao diện merchant;
   nếu không thấy tùy chọn, liên hệ VNPay. Không lấy key cũ trong chat dùng lại.
2. AWS Console → chọn **Singapore (ap-southeast-1)** → Secrets Manager →
   `shop/prod/application` → Retrieve secret value → Edit → **Plaintext**.
   Đây là secret env-file đang có, không tạo JSON hay thay toàn bộ nội dung.
3. Giữ nguyên DB/runtime/migration/JWT/S3 và các cấu hình khác. Thêm hoặc
   sửa **đúng một dòng mỗi key** dưới đây; thay hai placeholder bằng thông tin
   sandbox hiện hành từ VNPay, không lưu placeholder vào secret thật:

```dotenv
VNPAY_TMN_CODE=<MA_SANDBOX_HIEN_HANH>
VNPAY_HASH_SECRET=<KHOA_SANDBOX_MOI_DA_ROTATE>
VNPAY_PUBLIC_BASE_URL=https://aydibro.click
VNPAY_ALLOWED_HOSTS=aydibro.click
VNPAY_CHECKOUT_TTL=10m
VNPAY_BANK_CODE=
PAYMENT_PROVIDER_TYPE=none
PAYMENT_REFUND_ENABLED=false
VNPAY_QUERY_ENABLED=false
PAYMENT_RECONCILIATION_ENABLED=false
ORDER_PAYMENT_AUTO_INITIATION_ENABLED=false
ORDER_PAYMENT_EVENT_CONSUMPTION_ENABLED=false
ORDER_PAYMENT_RECOVERY_ENABLED=false
```

4. Không thêm `VNPAY_PREVIOUS_HASH_SECRETS` chứa khóa bị lộ vào giai đoạn
   chưa có giao dịch VNPay. Nếu trước đó thực sự có giao dịch pending, dừng
   để chốt kế hoạch rotation/drain riêng, không bỏ khóa/endpoint mù.
   Không thêm ADMIN credential trở lại. Bấm Save/Store rồi **chỉ báo trạng thái**,
   không gửi ảnh Plaintext, password, checksum hoặc secret vào chat.
5. Chạy lại preflight. Khi merchant/staging đạt, vẫn còn blocker forced-none:
   cần task candidate/validation/review riêng để thay bootstrap có kiểm soát.
   Save secret không tự restart EC2 hoặc rollout ASG. Agent chưa sửa secret hộ
   người dùng, chưa bump revision hay bật switch.

## Gate tiếp theo — chưa thực hiện

1. Sau khi người dùng xác nhận rotation/Store: đọc lại preflight và state mới;
   xác minh UI đang deploy, proxy/IP forwarding và không cache callback.
2. Chuẩn bị thay đổi hẹp từ **template đang deploy**, default none, chỉ opt-in
   sandbox; giữ AMI/image/IAM/DB/DNS/WAF. Phải có lint/Guard/account validation,
   change set và phê duyệt Execute riêng trước rollout. Không dùng lại plan cũ.
   Lưu ý template hiện 49.243 byte, gần giới hạn inline 51.200 byte; đo lại
   candidate, không nhồi thêm cấu hình để vượt giới hạn rồi upload tùy tiện.
3. Đăng ký IPN HTTPS với VNPay theo quy trình merchant. Dự kiến website
   `https://aydibro.click`, IPN `/api/payments/webhooks/vnpay`; chỉ xác nhận URL
   cuối sau kiểm tra deployed mode và endpoint.
4. Sau rollout được duyệt, người dùng tự thao tác màn hình thanh toán sandbox. Hệ thống
   phải nhận IPN, Order PAID và confirm kho đúng một lần; browser return không
   được tự xác nhận thanh toán. Cần đối chiếu cả failure/duplicate/late path.

Không gửi thông tin ngân hàng thật, mật khẩu, OTP hoặc secret vào README/chat.

IPN là server-to-server để cập nhật kết quả; Return URL là đường quay lại
trình duyệt, không thay IPN. Tham khảo [giao thức VNPay chính thức](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

## Kiểm thử và giới hạn nghiệm thu

- `node --experimental-test-coverage --test ci/aws-vnpay-preflight.test.cjs`:
  **52 test đạt**, 0 failure/skipped; helper line 100%, branch 85,92% theo Node
  experimental coverage (không phải JaCoCo của Java). Bao gồm exit 2 khi bị
  chặn, sai account/region/ARN, stack/image/fleet, version race, env parser,
  switch/default Spring và redaction trên cả đường thành công/lỗi.
- `node --check` cả hai file và `git diff --check` đạt. Test fixture không dùng
  AWS/merchant/credential thật; live preflight đọc riêng trả BLOCKED như trên.
- Không thay Java/schema/dependency/image/template/workflow nên không chạy lại
  Maven/Guard hoặc nhận coverage backend tăng từ test của helper. Gate trước
  PAY-ACCEPT-01 tại `43522e6`: 724 test local, line 91,69%, branch 72,42%, chưa
  merge/CI; không đánh dấu mục tiêu backend >80% hoặc milestone M5 đạt.
- Không push/PR/merge, không gọi giao dịch, không load test. Auto-ban giữ hoãn.
- 34 Guard finding và thiếu durable CloudTrail là finding lịch sử chưa xử lý
  trong task này, không phải kết quả audit mới ngày 24/10; chưa approval go-live.
