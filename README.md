# Shop

## 1. Mục tiêu

Xây dựng hệ thống thương mại điện tử bằng Java 21 và Spring Boot theo hai giai đoạn:

1. Hoàn thiện nghiệp vụ dưới dạng **modular monolith**.
2. Chỉ tách thành microservices sau khi luồng nghiệp vụ, dữ liệu, bảo mật và test đã ổn định.

Cách làm này giúp phát triển và debug nhanh ở giai đoạn đầu nhưng vẫn giữ ranh giới module đủ rõ để tách service về sau.

## 2. Trạng thái hiện tại

- Giai đoạn hiện tại: `M1 — Identity và RBAC`
- Nhiệm vụ vừa hoàn thành: `M1.5 — API my-info, cập nhật hồ sơ và đổi mật khẩu`
- Nhiệm vụ kế tiếp: `M1.6 — Admin quản lý user/role/permission` (**chưa bắt đầu**)
- Hạ tầng tài liệu API: Swagger UI/OpenAPI đã cấu hình sớm theo yêu cầu; Resilience4j được hoãn đến khi có outbound adapter thực tế.
- Kiến trúc triển khai hiện tại: một ứng dụng, một tiến trình, một MySQL.
- Kiến trúc đích: Gateway + Identity + Catalog + Inventory + Order + Payment.

Ký hiệu:

- `[ ]` Chưa làm
- `[~]` Đang làm
- `[x]` Đã hoàn thành và vượt qua tiêu chí nghiệm thu
- `[!]` Đang bị chặn, phải ghi rõ nguyên nhân

## 3. Nguyên tắc thực hiện

1. Chỉ thực hiện một nhiệm vụ chính tại một thời điểm.
2. Không chuyển nhiệm vụ tiếp theo khi test hoặc tiêu chí nghiệm thu của nhiệm vụ hiện tại chưa đạt.
3. Mỗi module sở hữu entity, repository và bảng dữ liệu của nó.
4. Module khác không được gọi trực tiếp repository hoặc class trong package `internal`.
5. Giao tiếp giữa module qua public interface, application service hoặc domain event.
6. Client không được quyết định giá tiền, quyền hạn, trạng thái thanh toán hoặc trạng thái đơn hàng.
7. Database thay đổi bằng Flyway; không dùng `ddl-auto=update`.
8. Không đưa secret hoặc mật khẩu production vào Git; JWT key hiện tại là key local/dev cố định theo source tham khảo và phải được thay thế trước khi deploy.
9. Mỗi endpoint phải có validation, phân quyền và error response nhất quán.
10. Mọi thao tác có thể bị gửi lặp như checkout, reserve stock và payment phải có idempotency.

## 4. Công nghệ nền

- Java 21
- Spring Boot 4.1.1
- Spring Modulith 2.1.1
- Maven Wrapper
- Spring MVC, Validation, Data JPA, Actuator
- Spring Security OAuth2 Resource Server và Nimbus JOSE/JWT
- Lombok cho DTO, entity, value object, service và controller boilerplate; MapStruct cho mapper compile-time
- MySQL 8.x; runtime hiện tại đã kiểm chứng với MySQL 8.0.46
- Flyway
- JUnit 5, Spring Boot Test, Spring Modulith Test, Testcontainers ở các giai đoạn cần database thật
- Docker và Docker Compose

Khi tách microservices sẽ bổ sung Spring Cloud theo release train tương thích, API Gateway, broker, tracing và cơ chế outbox.

## 5. Ranh giới module

```text
com.shop
├── shared         Error contract, correlation ID và technical utilities ổn định
├── identity       Người dùng, đăng nhập, JWT, role và permission
├── catalog        Category, product, SKU và product image metadata
├── inventory      Tồn kho, reservation, confirm và release
├── order          Cart, checkout, order và order item snapshot
└── payment        Payment attempt, provider adapter, webhook và refund
```

Quy tắc phụ thuộc dự kiến:

```text
identity     không phụ thuộc module nghiệp vụ khác
catalog      không phụ thuộc order/payment
inventory    chỉ nhận product/SKU ID, không truy cập CatalogRepository
payment      chỉ xử lý payment aggregate và provider
order        điều phối Catalog + Inventory + Payment qua API công khai/event
```

### 5.1. Phân tầng bắt buộc bên trong mỗi module

Identity là module mẫu; các module tiếp theo áp dụng cùng nguyên tắc:

```text
com.shop.identity
└── internal
    ├── captcha         Vertical slice của CAPTCHA thích ứng
    │   ├── configuration
    │   ├── dto
    │   │   ├── request
    │   │   └── response
    │   ├── entity
    │   ├── repository
    │   └── service
    ├── configuration   Cấu hình dùng chung của Identity như Security và Web MVC
    ├── controller      HTTP endpoint; chỉ gọi service, không gọi repository/entity
    ├── dto
    │   ├── request     Input contract + Bean Validation
    │   └── response    Output contract; tuyệt đối không trả entity trực tiếp
    ├── service         Transaction, use case và business rule
    ├── repository      Spring Data access thuộc riêng module
    ├── entity          JPA entity và invariant của domain
    ├── mapper          Chuyển entity/domain sang response DTO
    └── security        Security filter/config/adapter; không truy cập repository trực tiếp
```

Luồng chuẩn:

```text
Request → Controller → Request DTO validation → Service → Repository/Entity
        ← Controller ← Response DTO          ← Mapper  ←
```

Luồng CAPTCHA sau khi gom package:

```text
POST /api/auth/captcha
  → AuthenticationController
  → captcha.service.AdaptiveCaptchaService.issueChallenge
  → captcha.repository → captcha.entity

POST /api/auth/token
  → AuthenticationService
  → captcha.service.AdaptiveCaptchaService.verifyIfRequired
  → xác thực mật khẩu → ghi/xóa số lần thất bại

Scheduled cleanup
  → captcha.service.AdaptiveCaptchaCleanupService
  → xóa challenge và login-failure hết hạn

GET/PUT /api/auth/my-info
  → UserProfileController lấy username từ JWT đã xác thực
  → UserProfileService chỉ đọc/cập nhật đúng tài khoản hiện tại

PUT /api/auth/my-info/password
  → xác minh mật khẩu hiện tại → BCrypt mật khẩu mới
  → thu hồi mọi refresh-token family → access token cũ mất hiệu lực
```

Spring Modulith kiểm soát ranh giới giữa module; ArchUnit kiểm soát việc controller/security không truy cập persistence trực tiếp. Package `internal` không phải API cho module khác. Contract dùng chung phải được công khai có chủ đích bằng named interface, ví dụ `shared :: error`.

### 5.2. Ánh xạ Entity và bảng xác thực

Mỗi bảng do module Identity sở hữu đều dùng tên tiếng Việt không dấu, định dạng `snake_case` và tiền tố `xac_thuc_`. Mỗi entity khai báo tên bảng tường minh bằng `@Table`; các tên được gom tại `IdentityTableNames` để có một nguồn tham chiếu thống nhất trong code Java.

| Entity | Bảng database | Mục đích |
|---|---|---|
| `User` | `xac_thuc_nguoi_dung` | Tài khoản và hồ sơ người dùng |
| `Role` | `xac_thuc_vai_tro` | Vai trò hệ thống |
| `Permission` | `xac_thuc_quyen_han` | Quyền chi tiết |
| `RefreshToken` | `xac_thuc_phien_lam_moi` | Refresh-token rotation và trạng thái revoke |
| `LoginFailure` | `xac_thuc_dang_nhap_that_bai` | Bộ đếm login thất bại có thời hạn |
| `CaptchaChallenge` | `xac_thuc_thu_thach_captcha` | CAPTCHA một lần và thời điểm hết hạn |

Hai quan hệ nhiều-nhiều dùng bảng nối `xac_thuc_nguoi_dung_vai_tro` và `xac_thuc_vai_tro_quyen_han`. Architecture test bắt buộc mọi Identity entity phải có `@Table` với tiền tố `xac_thuc_`; Hibernate `ddl-auto=validate` tiếp tục đối chiếu mapping với Flyway schema khi khởi động.

Bảng hạ tầng `xac_thuc_khoa_captcha` không ánh xạ thành entity nghiệp vụ. Bảng chỉ chứa guard row do Flyway quản lý để tuần tự hóa thao tác cấp CAPTCHA giữa nhiều instance và giữ chính xác giới hạn dung lượng kho.

V1–V4 vẫn giữ nguyên tên bảng cũ trong file migration vì các migration đã chạy không được sửa checksum. V5 đổi tên bằng `ALTER TABLE ... RENAME TO`, nhờ đó database hiện có được nâng cấp mà không xóa hoặc tạo lại dữ liệu.

## 6. Danh sách nhiệm vụ

### M0 — Nền móng modular monolith

- [x] M0.1 Khởi tạo Maven project, Java 21, Spring Boot và Spring Modulith.
- [x] M0.2 Tạo package cho năm module và architecture test kiểm tra ranh giới.
- [x] M0.3 Cấu hình MySQL, Flyway, profile test và Docker Compose.
- [x] M0.4 Chuẩn hóa response lỗi, logging, correlation ID và health endpoint.
- [x] M0.5 Thiết lập format/check, unit test và CI cơ bản.
- [x] M0.6 Tạo Git baseline đã kiểm chứng, nhánh `develop` và đồng bộ GitHub remote.

Tiêu chí hoàn thành M0:

- `./mvnw clean verify` thành công.
- Spring context và kiểm tra Spring Modulith thành công.
- `docker compose config` hợp lệ.
- Ứng dụng khởi động được với MySQL và `/actuator/health` trả `UP`.
- Không có secret production trong repository; JWT key mặc định chỉ dùng cho local/dev.

### M1 — Identity và RBAC

- [x] M1.1 Thiết kế User, Role, Permission và migration.
- [x] M1.2 Đăng ký user; BCrypt/Argon2; role mặc định `USER`.
- [x] M1.3 Login với access token ngắn hạn và refresh token rotation.
- [x] M1.4 Logout/revoke token và dọn token hết hạn.
- [x] M1.4A Sửa validation contract, chống dò tài khoản, CORS allowlist, production secret và test MySQL thật.
- [x] M1.4B Rate limit riêng cho register/login/introspect/refresh/logout, trả `429` + `Retry-After` và giới hạn bộ nhớ bucket.
- [x] M1.4C CAPTCHA một lần có TTL, chỉ yêu cầu thích ứng sau số lần đăng nhập thất bại; không coi CAPTCHA là biện pháp chống DDoS duy nhất.
- [x] M1.4D Chuẩn hóa package cấu hình và tài liệu ánh xạ Entity → database table.
- [x] M1.4E Làm cứng CAPTCHA trước cạnh tranh dữ liệu và chuẩn hóa constants theo module.
- [x] M1.4F Việt hóa tên bảng Identity bằng migration giữ nguyên dữ liệu.
- [x] M1.4G Chuẩn hóa artifact, application name và JWT issuer thành `shop`.
- [x] M1.4H Gom CAPTCHA thành feature package và soát chất lượng toàn dự án.
- [x] M1.5 API `my-info`, cập nhật hồ sơ và đổi mật khẩu.
- [ ] M1.6 Admin quản lý user/role/permission.
- [ ] M1.7 Kiểm tra ownership trước khi ghi dữ liệu; không dùng `@PostAuthorize` cho update.
- [ ] M1.8 Unit, repository, controller và security integration test.

Tiêu chí hoàn thành M1:

- USER không thể tự cấp ADMIN hoặc sửa dữ liệu người khác.
- API role/permission chỉ dành cho ADMIN.
- Access token và refresh token tách biệt, có rotation/revoke test.
- Trước production, JWT key local/dev phải được thay bằng environment/secret riêng.

### M2 — Catalog và hình ảnh

- [ ] M2.1 Category, Product, SKU/variant và migration.
- [ ] M2.2 CRUD sản phẩm với DTO, validation và phân trang.
- [ ] M2.3 Upload nhiều ảnh qua abstraction `ObjectStorage`.
- [ ] M2.4 MinIO cho local; lưu metadata/URL thay vì Base64.
- [ ] M2.5 Tìm kiếm bằng query/index trước; chỉ dùng stored procedure sau benchmark.
- [ ] M2.6 Admin authorization, test file type/size và integration test.

Tiêu chí hoàn thành M2:

- Giá dùng `BigDecimal` kèm currency.
- SKU là đơn vị bán và là khóa tham chiếu của Inventory.
- File được kiểm tra kích thước/nội dung và tên lưu trữ không trùng.

### M3 — Inventory

- [ ] M3.1 Stock item, stock movement và stock reservation.
- [ ] M3.2 Reserve tồn kho bằng atomic conditional update/locking.
- [ ] M3.3 Confirm, release và expiration job.
- [ ] M3.4 Idempotency theo `reservationId`.
- [ ] M3.5 Concurrency test chứng minh không oversell.

Tiêu chí hoàn thành M3:

- Không thể reserve nhiều hơn `available_quantity`.
- Request gửi lặp không trừ kho hai lần.
- Reservation hết hạn được release an toàn.

### M4 — Cart, Checkout và Order

- [ ] M4.1 Cart và cart item.
- [ ] M4.2 Order state machine và quy tắc chuyển trạng thái.
- [ ] M4.3 Checkout tính giá hoàn toàn ở server.
- [ ] M4.4 Order item lưu snapshot tên, SKU, giá, giảm giá, thuế và currency.
- [ ] M4.5 Điều phối reserve/release inventory bằng domain event.
- [ ] M4.6 Idempotency key cho checkout.
- [ ] M4.7 Test lỗi giữa chừng và compensation.

Tiêu chí hoàn thành M4:

- Thay đổi giá sản phẩm không làm thay đổi đơn cũ.
- Không sinh hai đơn khi client retry cùng idempotency key.
- Mọi trạng thái order đều đi qua state machine hợp lệ.

### M5 — Payment

- [ ] M5.1 Payment aggregate và provider interface.
- [ ] M5.2 Fake payment provider để hoàn chỉnh luồng trước.
- [ ] M5.3 Tích hợp provider thật bằng hosted checkout/token; không lưu dữ liệu thẻ.
- [ ] M5.4 Xác minh chữ ký webhook và chống webhook lặp.
- [ ] M5.5 Payment success/failure/refund cập nhật Order qua event.
- [ ] M5.6 Reconciliation job cho trạng thái không chắc chắn.

Tiêu chí hoàn thành M5:

- Webhook lặp không tạo side effect lặp.
- Payment thành công mới confirm kho; thất bại/timeout phải release kho.
- Có audit trail cho mọi lần thử thanh toán.

### M6 — Hoàn thiện monolith

- [ ] M6.1 Transactional event/outbox trong monolith.
- [~] M6.2 OpenAPI và chiến lược versioning API.
  - [x] M6.2A Cấu hình nền Swagger UI/OpenAPI, Bearer JWT, security allowlist, production opt-in và integration test.
  - [x] M6.2A.1 Ngoại hóa metadata, contact và server URL bằng typed properties có validation; không áp dụng JWT toàn cục lên API công khai.
  - [ ] M6.2B Hoàn thiện mô tả operation/schema/response cho toàn bộ API sau khi các module nghiệp vụ được xây dựng.
- [ ] M6.3 Metrics, structured log và tracing.
- [ ] M6.4 Rate limiting phân tán tại gateway/edge cho checkout và các API tốn tài nguyên; login đã có lớp bảo vệ trong ứng dụng từ M1.4B.
- [ ] M6.5 Backup/restore và migration test.
- [ ] M6.6 End-to-end test toàn bộ purchase flow.
- [ ] M6.7 Load test các điểm login, search, checkout và reserve stock.
- [ ] M6.8 Threat review và dependency/security scan.
- [ ] M6.9 Audit trail và lịch sử truy cập.
  - [ ] M6.9A Tách access log kỹ thuật khỏi audit event nghiệp vụ; không ghi mọi request đọc thông thường vào database.
  - [ ] M6.9B Tạo entity và migration cho bảng append-only `he_thong_lich_su_truy_cap`, dùng UUID và thời gian UTC.
  - [ ] M6.9C Ghi nhận đăng nhập thành công/thất bại, logout, refresh/revoke token, thay đổi mật khẩu/hồ sơ, thao tác quản trị và thay đổi trạng thái nghiệp vụ quan trọng.
  - [ ] M6.9D Phát audit event tin cậy qua transaction/outbox; sự kiện bảo mật và thanh toán không được mất khi tiến trình bị lỗi.
  - [ ] M6.9E Cung cấp API quản trị có phân quyền để lọc, phân trang và xuất lịch sử; không có API sửa hoặc xóa từng bản ghi audit.
  - [ ] M6.9F Thiết lập index, retention/archive, giới hạn kích thước chi tiết, che dữ liệu cá nhân và test chống ghi trùng/lộ bí mật.

Hợp đồng dữ liệu dự kiến cho M6.9:

| Nhóm | Trường chính | Quy tắc |
| --- | --- | --- |
| Chủ thể | `actor_user_id`, `actor_username_snapshot`, `shop_id` | `actor_user_id` dùng UUID và được phép `null` cho đăng nhập thất bại; snapshot giúp tra cứu sau khi tài khoản đổi tên hoặc bị xóa. |
| Hành động | `action_code`, `feature`, `module`, `outcome` | Dùng mã ổn định thay vì chỉ lưu nhãn hiển thị; `outcome` tối thiểu gồm `SUCCESS`, `FAILURE`, `DENIED`. |
| Thời điểm/request | `occurred_at`, `ip_address`, `user_agent`, `correlation_id`, `http_method`, `request_path`, `http_status` | Lưu UTC; `correlation_id` liên kết với structured log và distributed trace. Chỉ tin proxy header khi request đến từ trusted proxy. |
| Đối tượng nghiệp vụ | `resource_type`, `resource_id`, `business_event_id` | Cho phép truy vết User, Product, Order, Inventory và Payment mà không tạo foreign key xuyên module/service. |
| Chi tiết | `detail_json` | Chỉ lưu metadata đã allowlist và giới hạn kích thước; cấm password, token, signing key, CAPTCHA answer, cookie và request/response body thô. |

Tiêu chí hoàn thành M6.9:

- Access log dung lượng lớn đi vào centralized logging với retention ngắn; database chỉ giữ audit event có giá trị bảo mật/nghiệp vụ hoặc lượt đọc dữ liệu nhạy cảm.
- Audit record là bất biến ở tầng ứng dụng; thao tác quản trị phải được phân quyền riêng và chính thao tác xuất lịch sử cũng được audit.
- Có index tối thiểu theo `(actor_user_id, occurred_at)`, `(module, occurred_at)`, `correlation_id` và `(resource_type, resource_id, occurred_at)`; mọi API dùng cursor/keyset pagination hoặc giới hạn phân trang rõ ràng.
- Chính sách retention, archive và quyền xem IP/user-agent được tài liệu hóa; test xác nhận không có secret/PII ngoài allowlist trong `detail_json`.

Điều kiện được phép bắt đầu tách microservices:

- Luồng đăng ký → đăng nhập → chọn sản phẩm → checkout → reserve → payment → hoàn tất chạy ổn định.
- Module verification, unit test, integration test và E2E đều đạt.
- API/event contract đã version hóa.
- Không module nào truy cập repository hoặc bảng thuộc module khác.
- Có idempotency, outbox, audit trail, metrics, trace ID và quy trình rollback migration.

### M7 — Tách microservices

- [ ] M7.1 Thêm API Gateway; monolith vẫn chạy phía sau gateway.
- [ ] M7.2 Tách Identity Service và chuyển các service sang xác minh JWT bằng public key/JWKS.
- [ ] M7.3 Tách Catalog Service và object storage.
- [ ] M7.4 Tách Inventory Service; thay lời gọi trong tiến trình bằng contract/event.
- [ ] M7.5 Tách Payment Service và webhook endpoint.
- [ ] M7.6 Tách Order Service cuối cùng vì đây là module điều phối nhiều nhất.
- [ ] M7.7 Mỗi service có database/schema, pipeline và health check riêng.
- [ ] M7.8 Broker + transactional outbox/inbox + retry + dead-letter queue.
- [ ] M7.9 Distributed tracing, centralized logs và dashboard/SLO.
- [ ] M7.10 Resilience test: timeout, retry, duplicate event và service unavailable.

Không tạo foreign key xuyên service. Việc tra cứu dữ liệu giữa service phải qua API hoặc event; Order giữ snapshot cần thiết để hiển thị lịch sử.

## 7. Tiến độ kiểm chứng

Ngày 2026-09-25:

- `mvnw.cmd clean verify`: thành công.
- Test: 26 chạy, 0 failure, 0 error, 0 skipped.
- Spring Boot context với profile test/H2: thành công.
- Spring Modulith architecture verification: thành công.
- `docker compose config`: hợp lệ.
- Runtime MySQL 8.0.46 qua container `mysql-8.0`, host port `3307`: thành công.
- `GET /actuator/health`: trả `UP` với MySQL thật.
- Flyway V1 tạo schema Identity; V2 tạo refresh-token store trên H2 và MySQL thật; Hibernate schema validation thành công.
- Testcontainers tự khởi tạo MySQL 8.0.46, chạy Flyway và kiểm tra role mặc định trong mỗi quality gate.
- `POST /api/auth/register`: trả `201 Created`; input có Bean Validation và lỗi trùng trả `409 Conflict`.
- Validation trả mã/message riêng cho required, length, format và tên quá dài; không còn placeholder `null` hay `INVALID_KEY` ngoài ý muốn.
- Password được BCrypt với cost 12; response không lộ password/hash; user mới chỉ nhận role `USER`.
- `POST /api/auth/token`: xác thực username/password và cấp riêng access token + refresh token.
- Login dùng dummy BCrypt cho username không tồn tại và chỉ lộ trạng thái disabled sau khi password đúng.
- Access token chứa `scope` từ role/permission; refresh token không chứa quyền và không được dùng để gọi resource được bảo vệ.
- `POST /api/auth/introspect`: kiểm tra chữ ký, issuer, audience, thời hạn và loại access token.
- `POST /api/auth/refresh`: xoay refresh token theo family; token cũ bị consume và việc tái sử dụng token cũ thu hồi toàn bộ family.
- `POST /api/auth/logout`: nhận refresh token, thu hồi idempotent toàn bộ token family; access token cùng family mất hiệu lực ngay.
- Cleanup job chạy định kỳ, chỉ xóa refresh token đã hết hạn; chu kỳ mặc định một giờ.
- Refresh token thô không lưu trong database; chỉ lưu SHA-256 hash. JWT signing key hiện theo đúng form source cho local/dev và phải externalize trước production.
- Smoke test MySQL thật xác nhận schema V2, đăng ký `201`, login/introspect/refresh/logout `200`, access trước logout `200` và sau logout `401`, logout lặp lại `200`, refresh sau logout trả `1014`, token hỏng trả `1014`; dữ liệu thử đã được dọn sạch.
- ArchUnit xác nhận controller/security không gọi trực tiếp repository; Spring Modulith xác nhận module Identity chỉ dùng các named interface `shared :: error` và `shared :: web`.
- Success/error response dùng chung `ApiResponse<T>` với mã số ổn định; không trả chi tiết exception nội bộ.
- `GlobalExceptionHandler` dùng chung xử lý `AppException`, validation, access denied, request sai định dạng, 404, method sai và lỗi ngoài dự kiến.
- CORS chỉ cho phép origin cấu hình bằng `CORS_ALLOWED_ORIGINS`; mặc định local là `localhost:3000` và `localhost:5173`.
- Profile `prod` bắt buộc truyền `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SIGNER_KEY` và `CORS_ALLOWED_ORIGINS`; không dùng fallback local.
- `X-Correlation-ID` được truyền qua response và MDC; giá trị không hợp lệ được thay bằng UUID.
- Spotless format/check được gắn vào Maven `verify`.
- GitHub Actions CI chạy Java 21 và `mvnw clean verify`.

Ngày 2026-09-26:

- `mvnw.cmd spotless:apply clean verify`: thành công với 32 test, 0 failure, 0 error, 0 skipped.
- MySQL Testcontainers 8.0.46, Flyway, Hibernate validation, ArchUnit, Spring Modulith, Spotless và JaCoCo đều hoàn thành.
- Rate limit token-bucket áp dụng riêng cho năm endpoint Identity/Auth; trả `ApiResponse` mã `1024`, HTTP `429` và `Retry-After`.
- Test xác nhận `X-Forwarded-For` giả không đổi bucket, client khác có bucket riêng, endpoint ngoài auth không bị giới hạn và bộ nhớ bucket có trần.
- Smoke test với MySQL Docker thật: hai lần login sai trả `401`, lần thứ ba trả `429`/`1024`, health vẫn trả `200`; ứng dụng đã graceful shutdown và không tạo dữ liệu thử.
- Bổ sung `springdoc-openapi` 3.1.1 tương thích Spring Boot 4; `/swagger-ui.html` và `/v3/api-docs` truy cập không cần JWT ở local/test, có Bearer JWT scheme và chỉ quét `/api/**`.
- Profile `prod` tắt Swagger UI/OpenAPI mặc định; chỉ bật lại có chủ đích bằng environment.
- `mvnw.cmd spotless:apply clean verify` sau khi thêm OpenAPI: thành công với 34 test, 0 failure, 0 error, 0 skipped; integration test xác nhận cả JSON contract và Swagger UI.
- Đã đánh giá cấu hình Resilience4j của `section3`; chưa thêm dependency vào monolith vì chưa có outbound HTTP adapter cần Circuit Breaker.
- `mvnw.cmd spotless:apply clean verify` sau M1.4C: thành công với 39 test, 0 failure, 0 error, 0 skipped.
- Flyway V3 tạo kho dùng chung cho số lần login thất bại và CAPTCHA; migration cùng Hibernate schema validation đã chạy thành công trên H2 và MySQL 8.0.46.
- Sau ba lần sai trong cửa sổ 15 phút, login yêu cầu CAPTCHA bằng HTTP `428`/mã `1025`; username không tồn tại áp dụng cùng chính sách để không lộ tài khoản.
- CAPTCHA là ảnh PNG sinh bằng `SecureRandom`, sống hai phút, gắn với hash của username và bị tiêu thụ đúng một lần kể cả khi đáp án sai hoặc password tiếp tục sai.
- Database chỉ lưu SHA-256 của username và đáp án gắn với challenge ID; API không trả đáp án, không ghi đáp án/secret vào log và có cleanup định kỳ cho dữ liệu hết hạn.
- `POST /api/auth/captcha` có rate limit riêng 10 request/phút mặc định; integration test xác nhận response, TTL, one-time use, replay, expiry, unknown username, cleanup và OpenAPI contract.
- Smoke test trên container local `mysql-8.0` cổng `3307`: Flyway nâng schema `shop` từ V2 lên V3, Hibernate validation thành công, health trả `UP` và OpenAPI có `/api/auth/captcha`; dữ liệu CAPTCHA/failure sau kiểm tra đều rỗng.
- M1.4D gom `*Configuration` và `*Properties` vào `identity.internal.configuration`; architecture test ngăn cấu hình bị đặt lẫn trở lại và bắt buộc mọi Identity entity dùng `@Table` với tiền tố module tường minh.
- `mvnw.cmd spotless:apply clean verify` sau M1.4D: thành công với 41 test, 0 failure, 0 error, 0 skipped; MySQL 8.0.46 và schema Flyway V3 tiếp tục tương thích, không cần migration đổi tên bảng.
- M1.4E chuyển việc tăng bộ đếm login thất bại sang atomic upsert, dùng database guard row khi cấp CAPTCHA và đặt unique constraint theo principal; các request đồng thời không còn làm mất lượt thất bại, vượt sức chứa hoặc tạo nhiều challenge cho cùng tài khoản.
- `POST /api/auth/captcha` chỉ cấp challenge sau khi principal đạt ngưỡng login thất bại; gọi sớm trả HTTP `409`/mã `1028` thay vì cho phép sinh CAPTCHA không cần thiết.
- Flyway V4 tạo `identity_captcha_store_locks`, loại bản ghi challenge trùng trước khi thêm unique constraint và đã chạy thành công trên H2 2.3 cùng MySQL 8.0.46.
- Các đường dẫn auth được gom trong `IdentityApiPaths`; mã role hệ thống nằm trong `RoleCode`. Constants được giới hạn trong module Identity thay vì tạo một `BusinessConstants` toàn cục dễ trở thành nơi chứa hỗn tạp.
- `mvnw.cmd spotless:check clean verify` sau M1.4E: thành công với 45 test, 0 failure, 0 error, 0 skipped; gồm test cạnh tranh dữ liệu CAPTCHA/login-failure và test migration trên MySQL thật bằng Testcontainers.
- Smoke test trên container local `mysql-8.0` cổng `3307`: Flyway nâng schema `shop` từ V3 lên V4, Hibernate validation thành công, health trả `UP`; tiến trình kiểm tra dùng cổng ngẫu nhiên và đã graceful shutdown để không ảnh hưởng ứng dụng ở cổng `8080`.
- M1.4F thêm `IdentityTableNames` và Flyway V5 để đổi chín bảng từ tiền tố `identity_` sang tên tiếng Việt không dấu `xac_thuc_*`; không sửa V1–V4 và không làm mất dữ liệu hiện có.
- H2 2.3 và MySQL Testcontainers 8.0.46 đều áp dụng đủ năm migration, Hibernate schema validation thành công và dữ liệu role từ V1 vẫn được đọc qua tên bảng mới sau V5.
- `mvnw.cmd spotless:apply clean verify` sau M1.4F: thành công với 45 test, 0 failure, 0 error, 0 skipped; 73 file Java và toàn bộ tài liệu đều đạt format check.
- M1.4G thống nhất Maven artifact/name, Spring application name và JWT issuer mặc định thành `shop`; test profile dùng issuer `shop-test`.
- `mvnw.cmd spotless:apply clean verify` sau M1.4G: thành công với 45 test, 0 failure, 0 error, 0 skipped; artifact đầu ra là `shop-0.0.1-SNAPSHOT.jar`.
- M1.4H gom toàn bộ configuration, request/response DTO, entity, repository và service của CAPTCHA vào `identity.internal.captcha`; package gốc vẫn giữ phân tầng con để luồng dễ đọc và dễ tách service sau này.
- Architecture test được mở rộng để kiểm soát các tầng lồng trong feature package và ngăn component CAPTCHA bị đặt rải rác trở lại.
- `mvnw.cmd spotless:check clean verify` sau M1.4H: thành công với 46 test, 0 failure, 0 error, 0 skipped; H2, MySQL 8.0.46, Flyway V5, Spring Modulith, ArchUnit, Swagger và Spotless đều đạt.

Ngày 2026-09-27:

- M0.6 tạo commit baseline trên nhánh `develop`, cấu hình `origin` và đồng bộ repository GitHub mà không đưa `.env`, mật khẩu máy cá nhân hoặc private key vào Git.
- M1.5 thêm `GET /api/auth/my-info`, `PUT /api/auth/my-info` và `PUT /api/auth/my-info/password`; cả ba endpoint bắt buộc access token.
- Controller luôn lấy username từ JWT đã xác thực, không nhận `userId` từ client; người dùng không thể dùng API hồ sơ để sửa tài khoản khác.
- Cập nhật hồ sơ chuẩn hóa email về chữ thường, chống email trùng, chuẩn hóa tên tùy chọn và đặt lại `emailVerified=false` khi email thay đổi.
- Đổi mật khẩu xác minh mật khẩu hiện tại, không cho dùng lại mật khẩu cũ, BCrypt mật khẩu mới và thu hồi toàn bộ phiên của tài khoản trong cùng transaction.
- Integration test phát hiện và đã sửa persistence context cũ sau bulk revoke; refresh token vừa bị thu hồi không thể được dùng lại trong transaction dài.
- OpenAPI công bố đủ ba endpoint mới với Bearer JWT security requirement.
- `mvnw.cmd spotless:apply clean verify` sau M1.5: thành công với 54 test, 0 failure, 0 error, 0 skipped; H2, MySQL 8.0.46, Flyway V5, Spring Modulith, ArchUnit, Swagger và Spotless đều đạt.
- M6.2A.1 học phần phù hợp từ `NT_KHCN_DMST_QG`: metadata, contact và server URL của OpenAPI được cấu hình theo môi trường bằng immutable typed properties có validation.
- Không sao chép global security requirement; integration test xác nhận register, token và CAPTCHA vẫn là API công khai trên tài liệu, còn `my-info` tiếp tục yêu cầu Bearer JWT.
- `mvnw.cmd spotless:check clean verify` sau M6.2A.1: thành công với 55 test, 0 failure, 0 error, 0 skipped; MySQL 8.0.46, Flyway V5, Spring Modulith, ArchUnit, OpenAPI, Spotless và JaCoCo đều đạt.
- Đã phân tích 47.588 bản ghi mẫu trong `ht_lich_su_truy_cap.xlsx` và bổ sung backlog M6.9; giữ các trường actor/action/module/time/IP/business reference hữu ích, loại bỏ hai trường rỗng hoàn toàn và bổ sung outcome, correlation ID, user-agent, HTTP/resource metadata, retention cùng quy tắc không lưu secret.
- M6.9 hiện chỉ là thiết kế tương lai, chưa tạo entity, migration hoặc API và không thay đổi nhiệm vụ kế tiếp M1.6.

Toàn bộ M0, M1.1, M1.2, M1.3, M1.4, M1.4A, M1.4B, M1.4C, M1.4D, M1.4E, M1.4F, M1.4G, M1.4H, M1.5 và M6.2A.1 đã vượt quality gate. Dừng tại đây theo nguyên tắc một nhiệm vụ; M1.6 chưa bắt đầu.

## 8. Luồng nghiệp vụ đích

```text
Client gửi checkout + Idempotency-Key
  → Order đọc giỏ hàng và lấy giá hiện tại từ Catalog API
  → Order tạo PENDING order + snapshot
  → Inventory reserve theo reservationId
  → Payment tạo payment attempt
  → Provider gửi signed webhook
  → Thành công: confirm inventory, Order → PAID
  → Thất bại/timeout: release inventory, Order → PAYMENT_FAILED/EXPIRED
```

Không sử dụng distributed database transaction. Luồng thất bại được xử lý bằng state machine, idempotency, event và compensation.

## 9. Chạy dự án

Yêu cầu: Java 21 và Docker.

```bash
docker compose up -d mysql
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
docker start mysql-8.0
.\mvnw.cmd spring-boot:run
```

Project mặc định kết nối MySQL tại `localhost:3307`. Nếu chưa có container `mysql-8.0`, có thể dùng `docker compose up -d mysql`; không chạy đồng thời hai container trên cùng port.

Ứng dụng dùng tài khoản MySQL riêng `shop` thay vì tài khoản quản trị `root`. Mật khẩu phải truyền qua `DB_PASSWORD` khi khác giá trị local mặc định; không ghi mật khẩu máy cá nhân vào source hoặc README.

Kiểm tra:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/swagger-ui.html
GET http://localhost:8080/v3/api-docs
```

Swagger UI và OpenAPI JSON được mở không cần JWT trong môi trường local để thử API. Nút `Authorize` sử dụng access token theo Bearer JWT. Profile `prod` tắt cả hai endpoint theo mặc định; chỉ bật có chủ đích bằng `OPENAPI_ENABLED=true` và `SWAGGER_UI_ENABLED=true`.

Đăng ký user:

```http
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "customer.one",
  "email": "customer.one@example.com",
  "password": "Str0ngPassword!",
  "firstName": "Customer",
  "lastName": "One",
  "dateOfBirth": "1995-05-20"
}
```

Đăng nhập:

```http
POST http://localhost:8080/api/auth/token
Content-Type: application/json

{
  "username": "customer.one",
  "password": "Str0ngPassword!"
}
```

Sau ba lần đăng nhập sai trong 15 phút, cùng request trên trả HTTP `428`, mã `1025`. Client lấy CAPTCHA mới:

```http
POST http://localhost:8080/api/auth/captcha
Content-Type: application/json

{
  "username": "customer.one"
}
```

Response trả `captchaId`, ảnh `imageData` dạng PNG data URI và `expiresAt`; không trả đáp án. Gửi lại login với challenge còn hạn:

```http
POST http://localhost:8080/api/auth/token
Content-Type: application/json

{
  "username": "customer.one",
  "password": "Str0ngPassword!",
  "captchaId": "id-tu-api-captcha",
  "captchaAnswer": "ma-tren-anh"
}
```

Mỗi CAPTCHA chỉ dùng một lần và mặc định hết hạn sau hai phút. Login thành công xóa bộ đếm thất bại. Có thể điều chỉnh bằng `AUTH_CAPTCHA_*`, nhưng không tắt CAPTCHA hoặc rate limit ở production nếu chưa có lớp bảo vệ tương đương tại edge.

Các endpoint xác thực và hồ sơ đã hoàn thành đến M1.5:

```text
POST /api/auth/register    Đăng ký tài khoản với role USER
POST /api/auth/token       Cấp access token và refresh token riêng biệt
POST /api/auth/captcha     Sinh CAPTCHA một lần cho username
POST /api/auth/introspect  Kiểm tra access token
POST /api/auth/refresh     Rotation refresh token
POST /api/auth/logout      Thu hồi toàn bộ token family bằng refresh token
GET  /api/auth/my-info     Đọc hồ sơ của tài khoản trong access token
PUT  /api/auth/my-info     Cập nhật hồ sơ của tài khoản trong access token
PUT  /api/auth/my-info/password  Đổi mật khẩu và thu hồi mọi phiên hiện có
```

Đọc hồ sơ hiện tại:

```http
GET http://localhost:8080/api/auth/my-info
Authorization: Bearer access-token
```

Cập nhật hồ sơ; username, role, trạng thái và quyền không được nhận từ request này:

```http
PUT http://localhost:8080/api/auth/my-info
Authorization: Bearer access-token
Content-Type: application/json

{
  "email": "customer.updated@example.com",
  "firstName": "Customer",
  "lastName": "Updated",
  "dateOfBirth": "1995-05-20"
}
```

Đổi mật khẩu:

```http
PUT http://localhost:8080/api/auth/my-info/password
Authorization: Bearer access-token
Content-Type: application/json

{
  "currentPassword": "Str0ngPassword!",
  "newPassword": "An0therStrongPassword!"
}
```

Sau khi đổi mật khẩu thành công, toàn bộ access/refresh token cũ của tài khoản mất hiệu lực; client phải đăng nhập lại bằng mật khẩu mới.

Logout dùng body sau và có tính idempotent:

```http
POST http://localhost:8080/api/auth/logout
Content-Type: application/json

{
  "token": "refresh-token"
}
```

Access token mặc định sống 3.600 giây (1 giờ); refresh token sống 36.000 giây (10 giờ), theo `jwt.valid-duration` và `jwt.refreshable-duration`. `JWT_ISSUER` và `JWT_AUDIENCE` vẫn có thể cấu hình qua environment. Cleanup token hết hạn dùng `jwt.cleanup-interval` và `jwt.cleanup-initial-delay` theo milliseconds.

Chạy quality gate:

```powershell
.\mvnw.cmd clean verify
docker compose config
```

`clean verify` cần Docker đang chạy vì có integration test MySQL bằng Testcontainers.

Frontend local mặc định được phép chạy từ `http://localhost:3000` hoặc `http://localhost:5173`. Có thể thay allowlist bằng environment:

```powershell
$env:CORS_ALLOWED_ORIGINS="https://shop.example.com,https://admin.example.com"
```

Rate limit Identity/Auth mặc định chạy theo địa chỉ kết nối trực tiếp tới ứng dụng:

| Endpoint | Burst mặc định | Chu kỳ nạp lại |
|---|---:|---:|
| `POST /api/auth/register` | 5 | 1 phút |
| `POST /api/auth/token` | 10 | 1 phút |
| `POST /api/auth/captcha` | 10 | 1 phút |
| `POST /api/auth/introspect` | 60 | 1 phút |
| `POST /api/auth/refresh` | 30 | 1 phút |
| `POST /api/auth/logout` | 30 | 1 phút |

Khi hết lượt, API trả HTTP `429`, mã `1024` và header `Retry-After`. Giới hạn hiện lưu trong bộ nhớ của một JVM, tối đa 10.000 bucket và tự xóa bucket không hoạt động sau 10 phút. Ứng dụng cố ý không tin `X-Forwarded-For` do client có thể giả mạo; khi triển khai sau reverse proxy phải cấu hình trusted proxy và đặt thêm rate limit/WAF ở edge. Có thể điều chỉnh bằng `AUTH_RATE_LIMIT_*`; không tắt lớp này ở production.

Profile production không dùng signer key local. Cần truyền đầy đủ secret và kết nối database trước khi chạy:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://db-host:3306/shop"
$env:DB_USERNAME="shop"
$env:DB_PASSWORD="replace-me"
$env:JWT_SIGNER_KEY="replace-with-at-least-64-random-bytes"
$env:CORS_ALLOWED_ORIGINS="https://shop.example.com"
```

## 10. Nhật ký quyết định

### ADR-001: Modular monolith trước microservices

Trạng thái: Accepted.

Lý do: nghiệp vụ e-commerce có nhiều invariant và luồng bù trừ. Hoàn thiện chúng trong một tiến trình giúp giảm chi phí vận hành và debug. Spring Modulith được dùng để ngăn monolith trở thành codebase phụ thuộc chéo tùy tiện.

### ADR-002: Không sao chép nguyên trạng Identity Service tham khảo

Trạng thái: Accepted.

Giữ lại cấu trúc Controller → request/response DTO → Service → Repository/Entity, `ApiResponse<T>`, `AppException`/`ErrorCode`, Bean Validation, Lombok, MapStruct, Nimbus JOSE/JWT, form cấu hình `jwt.signerKey`/`valid-duration`/`refreshable-duration` và cách Spring Security ánh xạ claim `scope` thành authority. Signing key cố định hiện chỉ dành cho local/dev theo yêu cầu; trước production phải externalize. Không giữ các điểm không an toàn khác như `admin/admin`, dùng access token làm refresh token, lưu refresh token thô, kiểm tra quyền sau khi cập nhật và cho phép mọi user quản lý role/permission.

### ADR-003: Tách Order sau cùng

Trạng thái: Accepted.

Order là module điều phối Catalog, Inventory và Payment. Tách các dependency ổn định trước giúp Order chuyển từ lời gọi nội bộ sang API/event theo từng bước, thay vì thực hiện một lần chuyển đổi lớn.

### ADR-004: Không sao chép nguyên trạng CAPTCHA/gateway từ NT_KHCN_DMST_QG

Trạng thái: Accepted.

Dự án tham khảo có các ý tưởng hữu ích gồm CAPTCHA lưu Redis với TTL 120 giây, gateway tập trung, lịch sử truy cập, JPA auditing và Hibernate Envers. Tuy nhiên, kiểm tra CAPTCHA trong luồng login/token đang bị comment, endpoint sinh CAPTCHA chưa có rate limit, gateway không cấu hình request rate limiter, CORS cho phép `*` và JWT có secret fallback. Vì vậy chỉ học kiến trúc, không sao chép code hoặc cấu hình nguyên trạng.

Shop triển khai token-bucket rate limit tại ứng dụng trước để bảo vệ các endpoint auth tốn CPU. CAPTCHA là lớp defense-in-depth thích ứng sau ba lần đăng nhập thất bại, dùng challenge một lần có TTL và kho MySQL dùng chung giữa các instance. DDoS lưu lượng lớn vẫn phải được chặn ở reverse proxy/CDN/WAF; CAPTCHA không thay thế được giới hạn kết nối và lưu lượng tại edge.

### ADR-005: Chỉ thêm Circuit Breaker tại ranh giới gọi ra ngoài

Trạng thái: Accepted.

Dự án `section3` bật Spring Cloud OpenFeign Circuit Breaker và đặt TimeLimiter mặc định 5 giây cho các lời gọi từ Account Service sang Notification/Statistic Service. Ý tưởng bảo vệ outbound call là đúng, nhưng cấu hình mẫu chưa chỉ định sliding window, số call tối thiểu, failure/slow-call threshold và hành vi half-open; fallback chỉ ghi log rồi bỏ qua lỗi nên không phù hợp cho payment hoặc thao tác bắt buộc phải thành công.

Shop hiện là modular monolith và Identity không có HTTP client gọi dịch vụ ngoài, vì vậy chưa thêm Resilience4j hoặc Spring Cloud chỉ để tạo cấu hình không được thực thi. Circuit Breaker sẽ được thêm theo từng outbound adapter khi tích hợp object storage, payment provider hoặc khi tách microservice. Mỗi dependency có instance riêng, timeout hữu hạn, fallback theo nghiệp vụ, metrics và test cho trạng thái closed/open/half-open; retry chỉ dùng với thao tác an toàn hoặc có idempotency.
