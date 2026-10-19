# Kế hoạch hoàn thiện website Shop

## Quyết định và phạm vi

Backend tiếp tục modular monolith ngay tại gốc `shop/` (`src/`, `pom.xml`, Maven wrapper, Dockerfile); UI là project riêng `shop-ui/` đặt cạnh `shop/`. Không còn lớp `backend/`. Đây là tách source/build frontend, không phải tách microservices. Mô hình triển khai dưới đây là đề xuất cho quy mô hiện tại, không phải chứng nhận tuân thủ hoặc một kiến trúc bắt buộc cho mọi doanh nghiệp. Chưa dùng Kubernetes; không public cổng MySQL hay dùng payment fake để nhận đơn thật.

Task đầu tiên đã triển khai và nghiệm thu local là [ROOT-01 — Chuẩn hóa layout và kiểm chứng build](backend-root-layout.md), theo yêu cầu bỏ thư mục backend; chưa push/merge/CI. Đã xác minh ngày 16/10/2026: source có đủ migration tới V24 qua `db/migration` + `db/mysql` (hoặc `db/h2` cho test); hai schema local cũng ở V24. Không nhầm version lớn nhất trong thư mục chung V23 với version toàn bộ migration. Gate build/schema phải dựa trên đúng locations và checksum, không dựa vào một thư mục hoặc README đơn lẻ.

**WEB-01 — Khách xem đơn hàng của chính mình** đã triển khai và nghiệm thu local sau ROOT-01: API ownership/paging và UI đọc lại từ server, không chỉ snapshot sau checkout. Bằng chứng và contract cụ thể ở [WEB-01](customer-order-history.md): 418 test backend và 6 test UI đạt, có MySQL và browser smoke riêng. Backend chưa push/merge/CI/deploy. UI đã xuất bản riêng lên [triramisu/shop-ui](https://github.com/triramisu/shop-ui), commit `1a03080`, chưa CI/deploy UI. Theo yêu cầu tiếp tục, [M5.5B.1 — Refund core](payment-refund-core.md) đã nghiệm thu local; B.2 provider/webhook và B.3 Order event còn lại. Không đánh dấu M5.5B, M5.6, M5.7 hoặc gate M6 hoàn thành từ kết quả phần B.1.

## Thứ tự triển khai theo rủi ro và phụ thuộc

### Quyết định ưu tiên ngày 18/10/2026

- Hoãn xây mới/mở rộng CI/CD, tự động push/deploy và cơ chế tăng MINOR Docker
  sau khi hoàn thiện chức năng, UI/backend và nghiệm thu tổng thể. Chưa tự
  nâng lên `1.0.0`; chưa có cơ chế tự tăng version được nghiệm thu.
- Giữ workflow/required checks và branch protection đã có. Không sửa hoặc
  tắt chúng trong task lập kế hoạch này; chưa thay đổi GitHub Actions,
  `CD_ENABLED`, `DEPLOY_ENABLED` hoặc tài nguyên AWS. Kiểm thử, build,
  security gate và kiểm tra migration vẫn thực hiện theo từng task.
- Nhiệm vụ gần nhất là khôi phục môi trường dev: MySQL/MinIO sẵn sàng,
  profile `dev` chọn `fake`, không bị Run Configuration ép sang Stripe.
  Prod dùng secret/runtime và migration credentials riêng; chưa có provider
  thật thì giữ `none`, tắt auto-initiation/refund, không dùng fake trong prod.
- Sau khi môi trường chạy được, hoàn thiện Payment theo mục tiêu VNPay,
  UI/backend và các luồng còn thiếu; phần cần merchant sandbox/HTTPS/IPN
  chỉ nghiệm thu khi có bằng chứng thật. Contract B.2/provider và thứ tự
  task phải được đối chiếu lại trước khi code, không mặc định bắt người dùng
  chuẩn bị Stripe. Adapter Stripe có sẵn không bị xóa trong quyết định này.
- Build/push/deploy thủ công chỉ thực hiện khi task hiện hành cần và được
  giao; không tự chạy giao dịch, migration vào DB thật hoặc tạo AWS resource.
  Sau gate tổng thể mới quay lại tự động hóa phát hành. Không đánh dấu task
  CI/CD hoàn thành, hoặc dự án hoàn thành, chỉ vì đã hoãn pipeline.

Theo yêu cầu trước đó, [S3-01](product-images-s3.md) được thực hiện trước
rollout AWS để bỏ phụ thuộc shared MinIO: adapter + bucket ảnh riêng/IAM,
giữ MinIO local. Đây không phải cho phép deploy, copy/xóa ảnh thật hay đóng
Payment. Payment vẫn còn việc; CI/CD được hoãn theo quyết định phía trên.

S3-01 đã nghiệm thu phần source/config/test local ngày 18/10/2026;
[bằng chứng và checklist cloud](product-images-s3.md) tách riêng việc kiểm
thử AWS/IAM, xuất bản image và chuyển ảnh thật vẫn chưa thực hiện.

Các mức dưới đây mô tả rủi ro cần phòng ngừa, không khẳng định đã tìm thấy lỗ hổng đang bị khai thác.

| Thứ tự | Mức | Nhiệm vụ / lý do | Việc người dùng cần cung cấp |
| --- | --- | --- | --- |
| 0 | Điều kiện chạy/build | ROOT-01: source backend tại gốc, cấu hình build/Compose đúng; bằng chứng schema V24 là baseline lúc nghiệm thu ROOT-01, không yêu cầu mở rộng CI/CD | Không cần thao tác thủ công |
| 0b | P0 — làm gần nhất | Khôi phục dev chạy độc lập provider thật, tách Run Configuration dev/prod và kiểm tra dependencies trước khi mở feature mới | Mở Docker Desktop; tự cấu hình credential đúng môi trường, không gửi secret vào chat |
| 1 | P0 | WEB-01: lịch sử và chi tiết đơn có ownership; chống xem đơn người khác, đọc lại trạng thái thật sau checkout | Không cần tài khoản ngoài |
| 2 | P0 | Payment: hoàn tiền có kiểm soát M5.5B, đối soát M5.6, VNPay sandbox M5.7 và UI chuyển cổng thanh toán; không thu/hoàn tiền trùng hoặc tin browser báo thành công | Đăng ký merchant sandbox, tự điền secret ở môi trường, đăng ký callback/notification URL |
| 3 | P0 | Outbox/inbox, audit, backup/restore, metrics/alert; phục hồi khi tiến trình dừng hoặc callback mất | Chốt môi trường, ngân sách, nơi lưu backup và người nhận cảnh báo |
| 4 | P1 | Người nhận/địa chỉ/phí giao hàng, snapshot checkout và xử lý giỏ sau đặt hàng không xóa nhầm thay đổi đồng thời | Chốt chính sách giao hàng, hủy/đổi trả; chưa bắt buộc tích hợp hãng vận chuyển |
| 5 | P1 | Backoffice sản phẩm/ảnh/tồn kho/đơn hàng với capability thật, optimistic version và audit | Chốt ai được làm STAFF và phạm vi quyền; không chia sẻ tài khoản ADMIN |
| 6 | P1 | Sửa hồ sơ/đổi mật khẩu UI, quên mật khẩu và xác thực email với token một lần/rate limit | Chọn dịch vụ email và xác minh tên miền gửi thư |
| 6b | P1 — trước rollout cloud | Source UI riêng, API compatibility, artifact version/rollback thủ công; adapter storage S3/IAM production (MinIO tiếp tục cho local/lab), secrets và TLS theo môi trường; chưa mở rộng CI/CD | Chọn repo UI, ngân sách AWS/region và quyền cấu hình môi trường |
| 7 | Gate nghiệm thu tổng thể | E2E gần production, security/container/dependency review, coverage branch toàn backend trên 80%, diễn tập rollback/restore rồi mới mở bán | Cấu hình AWS/DNS/TLS và phê duyệt phát hành; không cần gửi secret vào chat |
| 8 | Hoãn — sau hoàn thiện chức năng/gate tổng thể | CI/CD backend và frontend, tự động version/build/scan/push/deploy/rollback; lập task contract riêng khi tiếp tục | Cấp quyền/secrets phát hành khi đến task, không yêu cầu ngay để tiếp tục phát triển |
| Sau MVP | P2 | Wishlist đồng bộ, review, mã giảm giá, SEO nâng cao; microservices chỉ sau gate M6 | Chọn tính năng theo nhu cầu thật |

Security test, validation và regression chạy ở từng task, không đợi đến bước 7. Ngưỡng branch 65% hiện có không được coi là đạt mục tiêu cuối dự án trên 80%; không nâng ngưỡng rồi bỏ test hoặc loại mã nghiệp vụ khỏi báo cáo để làm đẹp số.

Baseline ROOT-01 từ 400 test: line 90,55%, branch 67,80%. Sau WEB-01 ngày 16/10/2026: 418 test, line 90,62%, branch 68,01%. Chưa đạt mục tiêu branch trên 80%. Khi thêm feature, phải thêm test nhánh đúng/sai/biên và failure recovery tương ứng; dành task tăng coverage cho nhánh nghiệp vụ thực sự còn thiếu trước release, sau đó mới nâng gate. Không coi đổi folder là cải thiện coverage.

B.1 đã nghiệm thu local ngày 16/10/2026: 459 test đạt, line 90,96%, branch
68,99%; V25, MySQL race/constraint và upgrade, quyền thật, OpenAPI/architecture,
3 Compose config và smoke JAR 8080 đạt. [Bằng chứng B.1](payment-refund-core.md).
Chỉ fake, tạo refund mặc định tắt; B.2/B.3/M5.6 còn lại, chưa mở hoàn tiền thật.
Backend chưa commit/push/CI; không gộp thay đổi layout/WEB-01 có sẵn hoặc file
skill đang staged của người dùng vào commit refund ngoài phạm vi.

## Việc người dùng tự chuẩn bị và điều kiện hoàn thành

- **Ngay lúc này:** mở Docker Desktop để kiểm chứng dev; không cần tự sửa SQL, di chuyển source hay cấp thêm quyền cloud cho CI/CD. Không đổi `.env` thật ngoài task được giao và không gửi secret trong chat. UI đã có remote GitHub do người dùng chọn và đã đẩy source; phần CI/CD mới được hoãn, không tự bỏ branch protection/required checks hiện có.
- **Trước task VNPay:** chuẩn bị tài khoản merchant sandbox, quyền quản trị cấu hình callback/IPN và HTTPS trên `aydibro.click` (đã mua miền, không cần mua lại). Tự đưa secret vào môi trường/secret manager, không đưa vào React hay Git. Chưa có sandbox/URL hợp lệ thì hoãn đúng phần phụ thuộc, vẫn có thể làm domain/test local không phụ thuộc provider thật.
- **Trước shipping/returns:** chốt hàng vật lý hay số, vùng giao hàng, cách tính phí, người nhận/địa chỉ và điều kiện hủy/hoàn. Không tự suy ra chính sách kinh doanh từ code demo.
- **Trước email:** chọn nhà cung cấp và xác minh DNS gửi thư; cần tài khoản/quyền DNS của người dùng, không yêu cầu gửi mật khẩu.
- **Trước production:** chốt ngân sách AWS, region, RPO/RTO và người nhận cảnh báo; phê duyệt cấu hình DNS/TLS/IAM/backup và diễn tập restore. Đây là hành động có chi phí/quyền ngoài source, không tự tạo tài nguyên từ task code.

Một task chỉ đóng khi có contract, code/config cần thiết, test trường hợp đúng/sai/biên, bằng chứng gate và rollback. Release cần purchase E2E + sandbox thật + bảo mật + restore + coverage branch toàn backend **trên 80%**, không chỉ build giao diện thành công. Mốc ngày trong README là mục tiêu, không phải cam kết phát hành khi các gate chưa đạt.

## Mô hình triển khai mục tiêu

```text
Người dùng
   │ HTTPS — aydibro.click
DNS (Cloudflare DNS hoặc Route 53; chọn một bên quản lý DNS)
   │
CloudFront + WAF
   ├── /*       → S3 private + OAC: React build
   └── /api/*   → ALB private (CloudFront VPC origin)
                       │
                  EC2 / Spring monolith trong private subnet
                       ├── Identity / Catalog / Inventory / Order / Payment
                       ├── RDS MySQL private
                       ├── S3 private: ảnh, URL ký có thời hạn
                       └── Payment provider / email qua outbound có timeout

Payment notification → /api/... → xác minh chữ ký + inbox → cập nhật nghiệp vụ
Secrets Manager / IAM role → ứng dụng (không đưa credential vào React)
Logs / metrics / alert + backup/restore → vận hành
```

Đây là đích triển khai, chưa tạo tài nguyên AWS trong task này. Bản lab có thể dùng Docker Compose và nginx hiện có, dữ liệu/credential riêng, giới hạn truy cập và tài nguyên; không thử tải phá hoại trên hạ tầng thanh toán hoặc môi trường bán hàng thật.

Repo vật lý không quyết định số service runtime: `shop/` vẫn là một backend modular monolith, `shop-ui/` tạo static bundle riêng. Bắt đầu một app instance; ALB/ASG/RDS và private networking được triển khai theo ngân sách, không yêu cầu Kubernetes hoặc microservices để hoàn thiện website. Mô hình cloud chỉ được coi là đã có sau khi kiểm tra AWS thực tế; tài liệu này không xác nhận hạ tầng hiện tại đã đáp ứng.

- S3 là REST origin private với OAC, không cần public bucket/website endpoint.
- API xác thực không cache ở CDN; forward Authorization và query đúng policy. Không fallback lỗi API thành `index.html`.
- Dùng CloudFront VPC origin khi region/tính năng hỗ trợ; cấu hình private subnet, security group, IPv4 cho ENI và internet gateway theo điều kiện AWS (không phải mở app ra Internet). Phương án ALB public phải chặn đường truy cập trực tiếp ngoài origin được phép. Không chồng nhiều CDN proxy mặc định nếu chưa có lý do vận hành.
- Chỉ ALB được vào cổng app; chỉ app được vào 3306. Ưu tiên SSM cho quản trị, không mở SSH/MySQL cho mọi IP.
- ASG chỉ nhân bản app stateless, không nhân bản MySQL/MinIO trong từng instance; hoàn tất shared state, job claim/idempotency và distributed rate limit trước khi tăng nhiều instance.
- TLS, secret riêng từng môi trường, payment fake bị chặn production, backup đã restore thử và health/rollback là điều kiện phát hành.

Nguồn nguyên tắc, đối chiếu ngày 16/10/2026: [OWASP Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html), [AWS S3 private origin và OAC](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html), [AWS CloudFront VPC origins](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-vpc-origins.html).

## WEB-01 — Hợp đồng nhiệm vụ

### 1. Mục tiêu và actor

Khách đã đăng nhập xem lịch sử/chi tiết đơn của mình, kể cả sau tải lại trang hoặc đăng nhập lại. Ngoài phạm vi: hủy đơn, thanh toán lại, refund, fulfillment, tra cứu đơn của người khác bằng quyền admin và dashboard quản trị.

### 2. Dữ liệu và phụ thuộc

Module Order sở hữu toàn bộ truy vấn. Tái sử dụng immutable order snapshot và enum trạng thái hiện có. Identity cung cấp Principal đã xác thực. Không đọc repository nội bộ của Payment/Inventory. Schema hiện tại là V24 khi nạp đầy đủ location chung và theo dialect. Index `(owner_subject, created_at)` đã có từ V16; dự kiến không cần migration cho API đọc.

### 3. API và bất biến

- `GET /api/orders?page=0&size=10`: page 0–10000, size 1–50, thứ tự cố định `createdAt DESC, id DESC`. Page trả danh sách summary cùng page/size/totalElements/totalPages/first/last và mô tả sort.
- `GET /api/orders/{orderId}`: snapshot hiện tại của đơn, không phải state do browser gửi.
- Cả hai bắt buộc JWT; owner chỉ lấy từ Principal, không nhận user ID/username từ client.
- Đơn không tồn tại và đơn không thuộc chủ sở hữu đều trả cùng `404 ORDER_NOT_FOUND`; kể cả ADMIN không được dùng API khách để xem đơn người khác.
- UUID/query sai trả 400; chưa đăng nhập trả 401. Các request chỉ đọc, không làm đổi tồn kho/payment/order.

### 4. Bảo mật, đồng thời và hiệu năng

Ownership nằm ngay trong điều kiện SQL cho cả page ID và batch load/detail. Không fetch-join collection cùng LIMIT/OFFSET; page ID/count trước, batch fetch snapshot theo owner sau để tránh phân trang trong RAM và N+1. Không trả owner, token, provider URL/secret. Không log payload đơn. Không phát sinh outbound call hoặc retry mutation.

### 5. Đầu ra

DTO/controller/service/repository và test backend; React API client/types, trang danh sách/chi tiết, liên kết từ hồ sơ/navigation và checkout; tài liệu kế hoạch này. Route xác nhận cũ vẫn hỗ trợ điều hướng sang đơn đã lưu trên server.

### 6. Trường hợp nghiệm thu

Đúng owner; cross-owner; anonymous; UUID sai; empty page; page/size biên và sai kiểu; ổn định khi timestamp bằng nhau; sort không bị tham số lạ ghi đè; reload chi tiết; query không làm đổi trạng thái; logout/đổi tài khoản không lộ cache đơn cũ; lỗi/rỗng/loading trong UI.

### 7. Kiểm chứng

Unit + MockMvc/security + H2 repository + MySQL Testcontainers cho truy vấn mới, OpenAPI + Modulith/ArchUnit, Maven verify phù hợp và frontend TypeScript/build; browser smoke bằng QA riêng. Ghi kết quả thực tế, không suy diễn CI xanh hoặc thanh toán sandbox đã đạt.

### 8. Rollout và rollback

API additive, không đổi schema/checkout contract cũ. Backend mới trước frontend mới. Rollback UI về image trước, rồi backend nếu cần; dữ liệu đơn giữ nguyên. Không xóa hoặc reset thay đổi frontend có sẵn. Chưa push/merge/deploy khi chưa có ủy quyền tương ứng.
