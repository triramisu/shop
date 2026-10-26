# PAY-SIT-01C — Nghiệm thu VNPay sandbox qua website

## Hợp đồng tám phần — 25/10/2026

1. **Mục tiêu/actor/phạm vi:** kiểm chứng khách USER mua sản phẩm DEMO qua
   website, nhận handoff VNPay sandbox và kết quả IPN thực từ provider. Chủ
   merchant tự đăng nhập/nhập dữ liệu sandbox khi cần. Không thanh toán thật,
   tự hoàn tiền, bật query/worker/recovery, load test hoặc sửa hạ tầng.
2. **Ownership/phụ thuộc:** Identity sở hữu phiên, Order sở hữu cart/order,
   Inventory sở hữu giữ/confirm/release kho; Payment sở hữu attempt và IPN
   inbox. Dùng API/public contract hiện hành, không SQL mutation xuyên module.
   Phải có đăng ký IPN được xác minh, runtime prod/vnpay và đúng UI đã deploy.
   Được cấu hình đúng URL IPN trên website merchant Shop đã xác định; giữ
   nguyên mã merchant, khóa, GET và HMACSHA512, rồi đọc lại sau khi lưu.
   Baseline deployed LT7 từ `570922c`; nhánh task xếp trên baseline vận hành
   chưa merge, không lấy `origin/main` làm bằng chứng đã chứa thay đổi đó.
3. **Luồng/bất biến:** Order/giá/currency do server tính. Payment chỉ thành
   công từ IPN hợp lệ; browser return không được tự đổi Order thành PAID.
   Replay cùng checkout key không tạo thêm order/giữ kho. Callback lặp không
   tạo thêm transition/inbox hoặc confirm/release movement.
4. **An toàn/vận hành:** chỉ merchant sandbox và sản phẩm DEMO, quantity1;
   giữ credential ở secret/session trong RAM, không ghi password/token/action
   URL/QR/checksum/query thô vào README, ảnh, terminal hoặc analytics. Không
   lấy lại ADMIN bootstrap từ secret cũ. Dùng USER đăng nhập riêng; không tạo
   public account với mật khẩu chung. Không đổi WAF/SG/CAPTCHA để test qua.
   Dừng tạo order mới nếu callback không về hoặc không xác minh được IPN.
5. **Đầu ra:** ma trận kịch bản, report kết quả có thời điểm và nguồn bằng
   chứng đã redaction; hướng dẫn merchant/IPN, kiểm trạng thái và xử lý pending.
   Chỉ sửa code/image/schema nếu thực sự phát hiện lỗi, lập phạm vi/test/plan
   riêng trước deploy. Không tạo thêm callback giả thành công để lấp bằng chứng.
6. **Ca kiểm tra:** success, provider-declared failure/cancel, callback lặp,
   return giả thành công, callback đến muộn/không về. Chỉ test duplicate bằng
   chức năng retry/SIT provider nếu có; không tự ký IPN thành công bằng merchant
   key rồi gọi đó là provider E2E. Cần phân biệt ca local với ca live.
7. **Nghiệm thu:** email/merchant xác nhận đúng IPN; app mode/health/current
   flags đạt; một order kiểm soát nhận IPN thực, Order PAID, Payment SUCCEEDED
   và kho confirm đúng một lần; ca thất bại giải phóng đúng một lần khi provider
   thực sự báo trạng thái final. Cần evidence theo order/attempt trong API/DB
   read-only, không chỉ screenshot Return00 hoặc HTTP200 của callback.
8. **Rollout/rollback/cleanup:** phạm vi ban đầu chưa yêu cầu rollout; lỗi
   phát hiện khi SIT có hợp đồng F1 và plan được duyệt riêng bên dưới.
   Giữ PaymentMode
   sandbox và endpoint IPN khi còn pending; không đổi none/xóa order/hạ Flyway/
   reset DB. Dọn cart/test session qua API hợp lệ sau khi ghi evidence, không
   xóa lịch sử tài chính/kho hoặc sửa tay trạng thái pending. Không push/merge
   hoặc gửi email hỗ trợ khi chưa có ủy quyền cho hành động đó.

## Tình trạng kiểm tra ngày 25/10/2026 (UTC+7)

- Working tree ban đầu sạch; `origin/main` fetch vẫn `847b093`. Nhánh mới
  `feature/payment/vnpay-sit-readiness` dựa trên `570922c` để khớp source đã
  triển khai; chưa push/merge. Schema source mới nhất V26, không thêm migration.
- CLI2.37.9 có sẵn nhưng phiên `shop-prod` hết hạn. Người dùng cho phép
  `aws login --profile shop-prod --region ap-southeast-1`; login thành công,
  STS xác nhận đúng account Shop. Không cài tool/đổi quyền hoặc secret.
- HTTP read-only15:15: UI200; Catalog200/totalElements12; anonymous
  `/api/auth/my-info`401. Ba request có header CloudFront, không tạo giao dịch.
- Snapshot AWS15:18: stack UPDATE_COMPLETE, mode vnpay-sandbox, revision
  trusted-proxy hiện hành; ASG2/2/4, hai AZ cùng LT7/InService/Healthy, đúng
  hai target Healthy, refresh gần nhất Successful. Không restart/rollout.
- Người dùng yêu cầu kiểm tra đăng ký IPN. Đã đọc metadata thư và thông báo
  delivery failure trong đúng Gmail của merchant, tìm cả spam/trash bằng
  query giới hạn ngày và tên miền provider; không đọc lại body chứa HashSecret,
  không tải attachment hoặc ghi nội dung thư/PII vào repo.
- Kết quả email: yêu cầu lúc16:52 ngày24/10 gửi tới `no-reply@vnpay.vn`.
  Yêu cầu khác lúc16:55 tới `support.vnpayment@vnpay.vn` bị Mail Delivery
  Subsystem trả **550 5.1.1 — tài khoản email không tồn tại**. Có bản nháp
  chưa gửi cùng người nhận. Không sửa/gửi lại/xóa thư hoặc draft.
- Tìm thư đến từ các tên miền VNPay từ24/10 chưa thấy kết quả xác nhận mới.
  Đây không chứng minh chắc chắn merchant chưa cấu hình qua kênh khác; phải
  kiểm tra merchant hoặc nhận xác nhận hỗ trợ. Email đăng ký tài khoản ngày
  19/10 không phải xác nhận IPN.
- Gmail đã truy cập được bằng kết nối hiện hành; không yêu cầu cài/kết nối
  lại. Người dùng đã tự đăng nhập merchant; dashboard đúng tên Shop, bộ lọc
  19–25/10 hiển thị tổng0 VND. Không dùng con số này để kết luận chắc chắn
  chưa từng có order/attempt hoặc IPN đã cấu hình.
- Đã kiểm tra menu Thông tin tài khoản và Cài đặt thông báo. Trang đầu có
  danh sách nhiều website sandbox; không thấy mã merchant Shop trong trang
  hiện tại. Không mở chi tiết website khác, không thay ID/đoán URL để truy
  cập, không copy danh sách hoặc thông tin liên hệ không liên quan vào repo.
  Cài đặt thông báo chỉ có trường Email/Đăng ký, không phải cấu hình IPN;
  không điền/gửi form. Chưa có bằng chứng URL IPN của Shop trong portal.
- Agent không đọc/lưu/đổi password, không gửi email hoặc thay cài đặt merchant.
  Kênh Trợ giúp/Liên hệ được portal công khai trỏ tới trang tư vấn chính thức
  `https://vnpay.vn/dang-ky-tu-van/`. Cần xác minh đúng nơi tiếp nhận sandbox
  trước khi truyền thông tin; không tự chọn người nhận từ danh sách website.

Các ghi nhận email/portal ở trên là lần kiểm tra ban đầu. Kết quả kiểm tra
tiếp theo dưới đây thay thế blocker chưa xác minh URL IPN; không xóa lịch sử
thư bounce hoặc coi việc đã gửi email là bằng chứng callback.

### Tiếp tục buổi tối — cấu hình merchant và regression

- Chủ merchant tự đăng nhập lại đúng tài khoản Shop. Trong **Thông tin tài
  khoản**, tìm chính xác dòng website bằng mã terminal của Shop và tên
  `shop aydibro`; chỉ mở link cấu hình của dòng đó. Không mở website khác,
  thay ID trong URL hoặc lưu danh sách merchant sandbox vào repo.
- Màn hình **Thông tin cấu hình** ban đầu có IPN URL là `#`; giao thức đã là
  **GET** và kiểu mã hóa **HMACSHA512**. Chỉ sửa IPN URL thành
  `https://aydibro.click/api/payments/webhooks/vnpay` và bấm **Hoàn thành**.
  Lượt bấm bị timeout ở công cụ nhưng máy chủ đã lưu; không bấm lại mù.
- Mở lại trang cấu hình của chính website đã chọn bằng GET mới, đọc lại
  được URL chính xác, GET và HMACSHA512. **Gate đăng ký IPN qua merchant
  đã đạt**; không cần đợi email hỗ trợ để thực hiện bước SIT tiếp theo.
  Chưa bấm Test call IPN hoặc tạo giao dịch. Không đọc/đổi khóa merchant,
  mật khẩu, quyền hoặc cấu hình của website khác.
- Ảnh chứng minh chỉ chứa cấu hình IPN, được lưu ngoài Git tại
  `%TEMP%/shop-vnpay-sit-20261025/merchant-ipn-config.jpg`; không chứa
  password, HashSecret, token hoặc URL thanh toán đã ký.
- Docker Engine ban đầu chưa chạy; khởi động Docker Desktop đã cài sẵn,
  không cài lại, reset hoặc xóa volume. Engine báo `29.6.1`.
- Tái sử dụng nguyên suite `VnpayPurchaseFlowMySqlTests` từ commit local
  `43522e6` vào nhánh SIT hiện tại; chỉ lấy file test, không ghi đè README
  hoặc nhập cả nhánh cũ. Blob hai bên khớp
  `96ec731ddc85275fcd5d0ecb34c686e68d9f04fe`. Không đổi Java production,
  schema, dependency, backend image, secret hoặc hạ tầng.
- Gate riêng lúc **21:04:46 UTC+7 ngày 25/10**: 16 test mua hàng PASS,
  0 failure/error/skipped trên MySQL **8.4.11**; Flyway V1–V26 và Hibernate
  validate đạt. Callback ở suite này dùng chữ ký fixture độc lập; vẫn là
  regression local, không phải callback thật của VNPay.
- 94 test Node preflight/template/trusted-proxy PASS. Lượt chạy đầu thiếu
  module YAML; chạy lại với `SHOP_YAML_MODULE`, `SHOP_BASH`, `SHOP_PYTHON`
  trỏ tới các công cụ đã cài theo runbook trusted-proxy. Không cài package
  mới, đổi PATH toàn máy, skip test hoặc sửa template để né kiểm tra.
- AWS preflight read-only: **32/32 check đạt**, blocker rỗng; fleet hai AZ
  cùng LT7, hai target Healthy, ASG2/2/4, không refresh đang chạy. Cấu hình
  merchant/switch trong AWSCURRENT đúng định dạng và version ổn định; không
  xuất nội dung secret. `PREFLIGHT_ONLY/readyForSit=false` là kết quả đúng
  của công cụ kiểm cấu hình, không được sửa thành bằng chứng giao dịch.
- Full `clean verify` kết thúc **21:19:55 UTC+7 ngày 25/10**, thời lượng
  10:06: **736 test PASS**, 0 failure/error/skipped; Spotless và JaCoCo gate
  hiện hành đạt. Backend line **91,73%** (6946/7572), branch **72,42%**
  (2345/3238), vẫn chưa đạt mục tiêu trên 80%. Không đổi ngưỡng hoặc loại
  code khỏi báo cáo. Website đang chờ chủ tài khoản tự đăng nhập USER riêng.
- Chủ tài khoản đã tự đăng nhập USER trong tab Shop của Codex; không lấy
  token/password từ browser storage. Probe SSM riêng trên hai EC2 LT7 chỉ
  xuất boolean: prod/vnpay, initiation/consumer=true, refund/query/worker/
  recovery=false, image khớp stack, container Healthy/restart0, có cấu hình
  merchant, không còn credential bootstrap ADMIN. Cả hai máy đạt 12 check.
- Baseline RDS qua JDBC read-only trong container: đúng MySQL8.4.11/schema
  Shop/runtime user, `sslMode=VERIFY_IDENTITY` và TLS cipher đạt; 3 danh mục,
  12 sản phẩm/24 SKU. Riêng `DEMO-AIR01-SILVER` có **onHand20/reserved0**
  trên đúng một stock row. Helper chẩn đoán tái dùng driver có sẵn trong JAR,
  chỉ SELECT/metadata, rollback giao dịch đọc; class tạm trong `/tmp` được
  xóa sau probe. Không cài driver/package, lấy master credential hoặc sửa DB.
- Giỏ USER ban đầu có cùng SKU quantity2; giảm qua UI thành quantity1,
  xác minh tổng server **490.000 VND** trước khi bấm xác nhận một lần.

**Chưa nghiệm thu PAY-SIT-01C:** URL IPN đã lưu và đọc lại thành công,
USER đã đăng nhập và tạo một đơn DEMO. Đơn bị hủy do sai location trước
payment; chưa có attempt/giao dịch/IPN thật. Đang sửa cấu hình kho theo F1
bên dưới. Không đóng M5 hoặc coi cấu hình, health/receipt97 là E2E thành công.

### Lỗi phát hiện khi chạy website — PAY-SIT-01C-F1

Đơn DEMO tạo lúc **21:25:18 UTC+7 ngày 25/10** tự chuyển CANCELLED trước
handoff. Evidence read-only theo đúng một order: orchestration và line đều
`FAILED/STOCK_ITEM_NOT_FOUND`, 0 payment attempt, 0 webhook, 0 payment event,
0 reservation và 0 movement. SKU vẫn onHand20/reserved0, location `DEMO-LAB`.
Checkout mặc định cấu hình location `MAIN`; không tìm kho khác để tự lấy hàng.
Đây là dữ liệu mẫu chưa khớp cấu hình checkout, chưa phải lỗi gateway VNPay.
Không sửa trạng thái hoặc xóa đơn đã hủy; không tạo đơn khác trước khi sửa.

Hợp đồng sửa cấu hình trước khi tiếp tục:

1. **Mục tiêu:** checkout sandbox dùng đúng kho của dữ liệu DEMO hiện hành;
   không đổi thuật toán thanh toán hoặc bật tính năng ngoài phạm vi.
2. **Đầu vào/ownership:** `application.yml` đã hỗ trợ
   `CHECKOUT_INVENTORY_LOCATION_CODE` (mặc định `MAIN`). Chủ tài khoản thêm
   `CHECKOUT_INVENTORY_LOCATION_CODE=DEMO-LAB` trong Plaintext secret ứng dụng;
   giữ các key khác. Không nhập `APP_ORDER...` nếu dùng biến đã được tài liệu hóa.
3. **Bất biến:** lookup giữ đúng variant/SKU/location; không fallback sang
   kho bất kỳ, sửa trực tiếp stock row hoặc giả xác nhận thanh toán.
4. **An toàn:** so sánh secret version/diff trong RAM, chỉ chấp nhận một key
   location thay đổi; không xuất nội dung secret. Giữ USER session và không
   dùng lại credential bootstrap ADMIN từ version cũ.
5. **Đầu ra:** tăng ConfigurationRevision bằng template đã deploy không sửa
   resource; plan mới cần thể hiện đúng phạm vi LT/ASG và phụ thuộc trước
   Execute. Cập nhật env example/runbook và regression non-default location.
6. **Kiểm tra:** location DEMO-LAB tạo reservation/payment được; location
   khác bị từ chối, không tạo attempt/giữ kho. Local dùng MySQL8.4, không
   dùng RDS/merchant thật để thay regression.
7. **Nghiệm thu sửa cấu hình:** hai EC2/target Healthy cùng LT mới, đọc đúng
   biến location và sandbox flags; dữ liệu kho chưa đổi. Sau đó mới tạo
   một đơn SIT mới và quan sát IPN thực; đơn bị hủy cũ giữ nguyên.
8. **Rollout/rollback:** rollout tuần tự do CloudFormation quản lý, có thể
   tạm ba EC2 tính phí. Không restart/terminate tay hoặc chạy tải đồng thời.
   Nếu cần rollback: phục hồi key location qua secret, revision mới và plan
   riêng; không hạ Flyway, tắt sandbox khi còn pending hoặc reset dữ liệu.

F1 đã kiểm trước Execute:

- Người dùng tự Store biến kho, agent không sửa nội dung secret. So sánh
  AWSCURRENT trong RAM với từng container: tất cả key hiện hành ngoài các
  override cố định của launch template khớp, chỉ location cần reload.
  Version ổn định ở cả hai probe; không in giá trị hoặc diff secret.
- Thêm regression non-default location qua chính biến
  `CHECKOUT_INVENTORY_LOCATION_CODE=DEMO-LAB`; 16 ca purchase hiện hữu dùng
  đúng kho đó, thêm một ca tồn kho ở `MAIN` bị từ chối mà không tạo attempt,
  reservation hoặc stock movement. **17/17 PASS** trên MySQL8.4.11 lúc
  **21:33:33 UTC+7**; Spotless chỉ format file test mới. Full regression
  sau thay đổi kết thúc **21:44:56 UTC+7** (10:31): **737 test PASS**,
  0 failure/error/skipped, Spotless/JaCoCo gate đạt. Line **91,69%**
  (6943/7572), branch **72,42%** (2345/3238); chưa đạt mục tiêu >80%.
  Đây là số đo mới, không lấy91,73% của lần736 test để thay thế.
- `.env.example`, env example prod/AWS và Compose dev/prod ghi rõ biến kho,
  truyền vào container với default MAIN giữ tương thích. Không copy tên kho
  DEMO vào default production của dự án khác; luôn đối chiếu stock thực tế.
- Sau cập nhật env example/Compose, chạy lại 94 test Node preflight/template/
  trusted-proxy PASS và `docker compose config --quiet` cho cả dev/prod với
  file example; không đọc `.env` thật, tạo container hoặc đổi runtime AWS
  bằng Compose. `git diff --check` đạt.
- Template source vẫn **52.108 byte**, SHA256
  `319e79f59c257c20c7d4fb2d12f70d82b9dedafe0e78e19c04e703d209d6e7b9`,
  khớp Original đang deploy; dùng `--use-previous-template`, không tải lại
  template khác/không sửa resource. cfn-lint1.57.2 không error/warning/info.
  Guard3.2.1/registry7f7340c: 34 failed Rule ID như baseline (32 report FAIL,
  44 PASS, 117 SKIP); giữ nguyên finding, không đổi rule để làm xanh.
  CloudTrail vẫn0 durable trail nên khả năng lưu audit dài hạn còn hạn chế.
- Bản nháp đầu dùng revision dài hơn32 ký tự bị AWS từ chối trước khi tạo
  change set; đã rút gọn `checkout-loc-20261025-01` và kiểm regex/độ dài trước
  khi tạo lại. Không có resource nào bị thay đổi từ bản nháp lỗi đó.
- Plan `checkout-location-20261025T143210Z`: AWS validation không có lỗi;
  default view3 Modify LT/ASG/scaling policy (ASG/policy Conditional), enhanced
  view2 Modify LT/ASG không replacement. Không Add/Remove; AMI/image vẫn ghim
  hiện hành. Người dùng duyệt plan cụ thể, agent kiểm lại stack/template,
  secret version, fleet/target, không refresh khác và plan không đổi rồi gửi
  Execute đúng một lần. Hiện stack UPDATE_IN_PROGRESS, LT8, native refresh
  InProgress; hai target LT7 cũ vẫn Healthy. Chưa nghiệm thu rollout.

Mốc theo dõi rollout F1 (UTC+7, không phải lệnh cần chạy lại):

- **21:35:50:** Execute được gửi một lần, client request token riêng; LT8
  và native refresh mới được tạo qua CloudFormation.
- **21:48–21:50:** EC2 mới AZ B nhập114 RDS CA, container khởi động hoàn tất,
  local health200/UP, Docker/ALB Healthy, restart0/OOM=false, location đúng
  DEMO-LAB, không có exception trong probe startup đã lọc. Chưa kết luận
  fleet hoàn tất chỉ từ một máy hoặc trạng thái ASG InService.
- **21:51:** ASG drain/terminate máy LT7 tại AZ B rồi khởi tạo LT8 ở AZ A;
  hai target phục vụ vẫn Healthy. Snapshot có4 instance ID gồm1 Terminating,
  không sửa capacity/policy hoặc terminate thủ công. Chưa tạo đơn SIT mới.
- **22:11:37 ngày25/10:** native refresh `c646918e-2f36-462a-aefa-8324651fa3eb`
  kết thúc Successful100% (Start21:36:09), không ép refresh/terminate.

### Hậu kiểm khi tiếp tục ngày26/10/2026 (UTC+7)

- Phiên CLI shop-prod đã hết hạn; chạy lại `aws login` theo ủy quyền gia hạn
  đã có. CLI2.37.9 có sẵn, login tự hoàn tất bằng phiên trình duyệt hiện hành;
  STS xác minh đúng account Shop/identity admin2. Không đổi profile mặc định,
  cài tool hoặc yêu cầu người dùng gửi credential/MFA vào chat.
- Snapshot **08:46:** stack UPDATE_COMPLETE, revision
  `checkout-loc-20261025-01`; ASG2/2/4, hai AZ cùng LT8/InService/Healthy,
  đúng hai target8080 Healthy; refresh Successful100%, không refresh khác.
  EC2 thực tế vẫn dùng AMI đã ghim, không chỉ suy diễn từ parameter.
- Probe SSM trên cả hai container: **24/24 check đều true**. Profile prod,
  provider vnpay, initiation/consumer=true; refund/query/reconciliation/
  recovery=false. Location đúng DEMO-LAB, đủ8 native flags, image khớp,
  Docker Healthy/restart0/OOM=false; không có credential bootstrap ADMIN.
  Các key AWSCURRENT ngoài override cố định khớp runtime trong RAM và
  version giữ nguyên. Chỉ lưu boolean, không xuất env/secret hoặc checksum
  có thể được dùng như token thanh toán.
- Preflight read-only32/32 đạt, blocker rỗng, template SHA/size không đổi;
  vẫn báo PREFLIGHT_ONLY/readyForSit=false theo đúng thiết kế. Probe runtime
  riêng đạt không được dùng để đổi công cụ thành chứng nhận SIT.
- JDBC read-only **08:48:** đúng RDS8.4.11/schema/runtime user và TLS; ba
  danh mục,12 sản phẩm/24 SKU; `DEMO-AIR01-SILVER` vẫn **20/0 tại DEMO-LAB**.
  Helper tạm đã được xóa khỏi container sau khi đọc; không mutation DB.
- Working tree đầu lượt sạch, HEAD local `6b134ed` chứa regression737 test
  và runbook. Không có thay đổi Java/schema mới nên không chạy lại toàn bộ
  regression đã đạt chỉ vì qua ngày; chưa push/merge. Giữ kết quả737 test
  lúc21:44:56 ngày25/10, không ghi nhầm là bộ test mới chạy ngày26/10.
- Tab merchant cũ đã hết phiên. Cấu hình IPN đã lưu/đọc lại từ ngày25/10;
  không cần đăng nhập merchant để tạo đơn. Tab Shop mới ở /login, đang chờ
  chủ tài khoản tự đăng nhập USER. Chưa tạo thêm order/attempt/handoff.

Quy trình tái sử dụng khi kho/dữ liệu mẫu chưa khớp:

1. DataGrip qua SSM hoặc probe read-only, kiểm đúng SKU/location/balance:

   ```sql
   SELECT sku, location_code, on_hand, reserved_quantity
   FROM ton_kho_mat_hang
   WHERE sku = 'SKU_CAN_KIEM_TRA';
   ```

2. Secrets Manager đúng region → secret ứng dụng → Retrieve secret value →
   Edit → Plaintext. Thêm/sửa **một dòng** theo location thực, không trùng key:

   ```dotenv
   CHECKOUT_INVENTORY_LOCATION_CODE=DEMO-LAB
   ```

   Đây là giá trị cho dữ liệu DEMO hiện tại. Kho thực MAIN thì dùng MAIN.
   Giữ nguyên DB/JWT/merchant/các switch khác, bấm Store. Không commit secret
   hoặc chụp màn hình Plaintext. Store chưa đổi environment của container cũ.
3. Tạo change set UPDATE bằng template đang deploy, giữ các parameter khác,
   chỉ tăng ConfigurationRevision (1–32 ký tự `a-zA-Z0-9._-`). Kiểm AMI vẫn
   ghim và không có refresh đang chạy. Chạy lint/Guard và AWS validation;
   review cả hai chế độ plan trước khi duyệt Execute riêng cho lần đó.
4. Execute đúng một lần sau duyệt. Không chạy lại plan dưới đây; lệnh này
   ghi lại lần đã được duyệt, không phải lệnh cần dùng lại cho dự án khác:

   ```text
   aws cloudformation execute-change-set --stack-name shop-aydibro-prod --change-set-name checkout-location-20261025T143210Z --profile shop-prod --region ap-southeast-1 --no-cli-pager
   ```

5. Chờ native refresh Successful, hai target Healthy cùng LT mới và runtime
   đọc đúng location. Bootstrap nhập RDS CA nên có thể mất30–35 phút cả đợt;
   không stop/start/terminate tay. Theo dõi bằng lệnh read-only trong
   [runbook trusted proxy](trusted-proxy.md#quy-trình-rollout-sau-khi-được-duyệt).
6. Sau hậu kiểm mới tạo một đơn test mới. Đơn cũ CANCELLED phải giữ nguyên;
   không sửa DB để hồi sinh đơn hoặc ghi đè ledger. Chỉ kết quả IPN hợp lệ
   mới xác nhận thanh toán; cấu hình đúng kho chưa phải SIT thành công.

### SIT sau sửa kho — lỗi chữ ký cần đối chiếu (26/10)

- Chủ tài khoản đã tự đăng nhập USER trong tab Codex. Từ giỏ một SKU DEMO
  quantity1, đọc quote490.000 VND rồi xác nhận đúng một lần lúc **08:55:21**.
  Đơn mới PENDING, payment REQUIRES_ACTION và UI hiện link VNPay hợp lệ.
  Không tạo thêm đơn khi provider từ chối.
- Handoff thật đã đến đúng `sandbox.vnpayment.vn/paymentv2/Payment/Error.html`;
  provider hiển thị **Sai chữ ký** lúc **08:55:50**, trước bước nhập thẻ/OTP.
  Ảnh chỉ chứa màn hình lỗi, ngoài Git tại
  `%TEMP%/shop-vnpay-sit-20261025/vnpay-signature-error-20261026.jpg`.
  Không dùng browser error để tự đổi Payment FAILED hoặc Order CANCELLED.
- Evidence RDS08:57: stock20/reserved1, một reservation RESERVED và một
  movement RESERVATION; Order PENDING, orchestration PAYMENT_PENDING,
  một attempt REQUIRES_ACTION490.000 VND, chưa có webhook, chưa confirm kho.
  Order payment event COMPLETED ở đây là event **REQUIRES_ACTION** đã được
  consumer xử lý, không phải bằng chứng thu tiền hoặc Order PAID.
- Cấu hình terminal khớp mã merchant đã đăng ký; secret32 chữ in hoa, không
  dấu nháy/khoảng trắng. Đối chiếu trong RAM với đúng hai email cấp merchant
  ngày19/10: cả hai email cùng khóa và khớp AWSCURRENT. Không lưu raw mail,
  khóa, fingerprint hoặc attachment vào repo; không đổi label/gửi email.
  Chưa thấy email cấp lại khóa sau24/10 trong query giới hạn nguồn provider.
- Probe không-follow-redirect trên **chính attempt này**, trước khi hết TTL:
  local handoff303, public CloudFront handoff303; token handoff khớp HMAC
  của key runtime. Độc lập decode/canonicalize URL Location và tính lại
  HMACSHA512: signature khớp, amount minor-unit khớp49000000,13 trường ký.
  Chỉ xuất boolean/count, không URL/token/signature/IP hoặc query thô.
  Probe không gọi tiếp gateway, gửi callback hoặc tạo payment mới.
- Tại thời điểm probe, chưa đủ bằng chứng kết luận lỗi code hay cấu hình
  phía provider; tiếp tục kiểm thuật toán/terminal qua merchant, không đổi khóa,
  tắt kiểm HMAC hoặc ký callback giả thành công để vượt qua gate.
- Handoff có TTL10 phút; không kéo dài hoặc retry link hết hạn. Inventory
  expiry hiện bật và có thể release reservation sau15 phút; phải đọc lại
  evidence. Hết TTL/reservation không phải bằng chứng provider thất bại;
  query/recovery/refund vẫn tắt, không sửa trực tiếp trạng thái hoặc xóa lịch sử.
- Hậu kiểm **09:16**: stock20/reserved0, reservation EXPIRED và đúng một
  movement EXPIRATION bên cạnh movement RESERVATION; chưa có CONFIRMATION,
  webhook hoặc event SUCCEEDED. Order vẫn PENDING/attempt REQUIRES_ACTION;
  line/orchestration giữ snapshot payment-pending theo thiết kế hiện hành.
  Không gọi trạng thái này là giao dịch đã thất bại hoặc đã được hủy.
- Người dùng sau đó cho biết đã đổi thuật toán **ở Merchant sang SHA256**
  hôm trước. Đã mở đúng trang cấu hình IPN đã quan sát trước đó, xác nhận
  IPN của Shop/GET còn đúng và ô Kiểu mã hóa thực tế là SHA256. Chỉ đổi ô
  này sang **HMACSHA512**, bấm **Hoàn thành một lần**. UI chuyển về trang
  tài khoản dù thao tác chờ snapshot báo timeout; không gửi lại form.
  Mở lại cấu hình xác nhận HMACSHA512/GET/IPN đúng đã được lưu.
  Không đổi mã merchant, key, code backend, image, secret hoặc AWS.
- Trang merchant danh sách rất dài, snapshot có giới hạn; không kết luận
  mất terminal chỉ từ phần danh sách chưa tải đủ, không mở/sửa terminal
  khác hoặc đoán ID. Chỉ làm trên trang Shop đã định danh bằng IPN chính xác;
  không copy thông tin liên hệ/danh sách website khác vào README.

### Handoff sau đồng bộ HMACSHA512 — 26/10, đang chờ nhập thẻ test

- Baseline SELECT-only trước ca mới: SKU DEMO-AIR01-SILVER tại DEMO-LAB có
  stock20/reserved0. Đơn trước chưa từng nhập thẻ và reservation đã EXPIRED;
  không sử dụng lại link hết TTL hoặc chỉnh lịch sử đơn cũ.
- Đọc lại quote490.000 VND/quantity1; xác nhận đúng một lần lúc **09:31:22**.
  Đơn mới `0a0c2ca1-9c48-490a-8455-1adb86f0d68a`, PENDING, một attempt
  REQUIRES_ACTION. Handoff đến đúng hosted VNPay, hiển thị **Chọn phương
  thức thanh toán (Test)** rồi **Thanh toán qua Ngân hàng NCB**490.000 VND;
  lỗi Sai chữ ký không còn tái hiện trong ca mới sau đồng bộ thuật toán.
- Evidence trước nhập thẻ: stock20/reserved1, một reservation RESERVED và
  một movement RESERVATION; chưa có CONFIRMATION/webhook/SUCCEEDED. Event
  REQUIRES_ACTION/COMPLETED chỉ xác nhận consumer đã xử lý trạng thái chờ.
- Đã bàn giao trang NCB cho chủ tài khoản tự nhập thẻ/OTP sandbox theo
  [bộ test công khai của VNPay](https://sandbox.vnpayment.vn/apis/docs/gioi-thieu/).
  Không dùng thẻ thật, không agent chấp nhận điều kiện dịch vụ thay người dùng.
  Chưa coi gateway mở được là Order PAID hoặc IPN thành công. Chờ evidence
  provider và hậu kiểm read-only trước khi kết luận.
- Ảnh cấu hình đã lưu và màn hình NCB được giữ ngoài Git trong thư mục
  `%TEMP%/shop-vnpay-sit-20261025/`; không lưu signed URL/query/QR/token/key.

Phạm vi xử lý mismatch thuật toán trong cùng SIT:

1. Mục tiêu là đồng bộ cấu hình terminal với adapter HMACSHA512 đang chạy;
   không đổi thuật toán/key backend hay hạ kiểm tra chữ ký.
2. Đầu vào là đúng terminal của Shop và cấu hình merchant thực tế; secret
   hiện hành đã được đối chiếu trong RAM với email, không nhập lại vào chat.
3. Chỉ sửa lựa chọn thuật toán ở merchant, giữ IPN URL/GET; mở lại kiểm tra
   đã lưu, không làm việc trên terminal chưa xác định thuộc Shop.
4. Giữ nguyên lịch sử order/payment/reservation cũ; không tự gán FAILED,
   PAID hoặc xóa inbox chỉ từ kết quả trên trình duyệt.
5. Nếu chỉ lệch cấu hình merchant thì không cần code/schema/image mới;
   chỉ lập phạm vi release riêng nếu sau khi đồng bộ vẫn tìm thấy lỗi code.
6. Sau readback, một ca test mới có kiểm soát; không sử dụng handoff cũ hết
   TTL hoặc đổi idempotency key để lặp một request chưa rõ kết quả.
7. Chỉ nghiệm thu khi gateway nhận request, IPN hợp lệ cập nhật đúng attempt,
   Order PAID và một confirm kho; test fixture không thay bằng chứng provider.
8. Nếu vẫn sai chữ ký, dừng tạo thêm order, dùng mã tra cứu/thời điểm đã
   redaction để xác minh với provider; không gửi key/query ký hoặc tự liên hệ
   bên ngoài khi chưa có ủy quyền. Query/recovery/refund tiếp tục tắt.

## Trình tự tiếp tục

1. Chủ merchant đã tự đăng nhập
   [VNPay Merchant sandbox](https://sandbox.vnpayment.vn/merchantv2/Users/Login.htm)
   trong trình duyệt ứng dụng. Khi phiên hết hạn, tự đăng nhập lại; không gửi
   mật khẩu/OTP/ảnh credential vào chat.
2. Agent kiểm tra đúng website của Shop trên màn hình thực tế. Chỉ xác nhận IPN nếu
   có cấu hình hoặc phản hồi rõ ràng khớp URL:

   ```text
   https://aydibro.click/api/payments/webhooks/vnpay
   ```

   Trong portal hiện tại: **Thông tin tài khoản → dòng website Shop → tên
   website → Thông tin cấu hình**. Điền **IPN Url**, giữ **GET** và
   **HMACSHA512**, bấm **Hoàn thành** một lần, mở lại đúng cấu hình để kiểm
   tra đã lưu. Nếu nút báo timeout, đọc lại trước khi cân nhắc bấm lần nữa.
   Bước này đã thực hiện thành công ngày 25/10; không phải cấu hình trong
   mục thông báo email và không nhập HashSecret vào URL IPN.

   Nếu portal không có chức năng đó, dùng kênh hỗ trợ **đã xác minh hiện hành**.
   Không tiếp tục gửi vào địa chỉ bounce hoặc mặc định Reply tới no-reply sẽ
   được xử lý. Không đoán địa chỉ email thay thế; thư mới không đính kèm/
   forward email merchant có HashSecret. Tạo/gửi draft cần yêu cầu riêng.
3. Khi gate IPN đạt, kiểm lại fleet/runtime/image/secret flags. Chủ tài khoản
   đăng nhập USER riêng vào website; không dùng ADMIN để thay ownership và
   không đưa token vào chat/command line. Chỉ tạo tối đa một order cho mỗi
   kịch bản đã chọn trước; không retry checkout bằng key mới khi mất response.
4. Ghi baseline order/payment/inventory theo reference được phép trước khi
   tạo giao dịch. Chọn SKU DEMO còn tồn và quantity1; xác minh VND/giá do server
   tính. Chỉ mở handoff HTTPS có allowlist đã kiểm, không lưu URL đầy đủ.
5. Người dùng tự thao tác màn hình thanh toán sandbox bằng thông tin test
   chính thức; không dùng thẻ/tài khoản ngân hàng thật. Agent đối chiếu Order
   API và IPN/inbox/stock read-only, ghi kết quả theo ma trận sau.

| Kịch bản | Bằng chứng cần có | Trạng thái |
| --- | --- | --- |
| Sai location kho | Checkout hủy trước payment, không giữ/xuất kho, không tạo attempt | Đã quan sát trên website/RDS; đang sửa F1 |
| Success | IPN thực, đúng chữ ký/amount/reference; Payment SUCCEEDED; Order PAID; một confirm kho | Chưa chạy |
| Failure/cancel | Provider báo final failure, Order/kho chuyển đúng policy; không suy diễn từ đóng tab | Chưa chạy |
| Duplicate | Retry do provider/SIT thực, không thêm transition/inbox/movement | Chưa chạy |
| Return giả00 | Không đổi Order/stock khi chưa có IPN hợp lệ; tách evidence local/live | Chưa chạy live |
| Missing/late IPN | Dừng tạo thêm order, ghi pending/manual action, không sửa DB/giả success | Chưa chạy |

6. Nếu thiếu bằng chứng, báo đúng phần chưa đạt. Kill switch query/worker/
   recovery/refund vẫn giữ như baseline; không bật thêm để làm test xanh.
   Không nâng quyền hoặc dump DB/log thô để thu evidence.
7. Logout/revoke phiên test khi xong; xử lý cart và đơn còn pending theo API/
   quy trình nghiệp vụ, không xóa ledger/inbox/history. Ghi kết quả vào README.

## Phân biệt các loại bằng chứng

- **Metadata/health:** hệ thống có cấu hình và đang chạy, chưa có giao dịch.
- **Local regression:** callback ký bằng fixture, MySQL/H2; không phải VNPay gọi.
- **Provider SIT:** VNPay gửi callback theo kịch bản, có đối chiếu đơn/kho.
- **Go-live:** còn gate bảo mật/backup/outbox/credential và tiền thật riêng.

Guard34 finding và thiếu durable CloudTrail là baseline chưa được sửa;
coverage branch backend được đo lại ở lượt regression tối 25/10 là 72,42%,
chưa đạt mục tiêu >80%. Không tạo thêm tài nguyên AWS.

Nguồn giao thức: [VNPay PAY — IPN server-to-server](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
