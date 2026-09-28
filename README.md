# Shop

## 1. Mục tiêu

Xây dựng hệ thống thương mại điện tử bằng Java 21 và Spring Boot theo hai giai đoạn:

1. Hoàn thiện nghiệp vụ dưới dạng **modular monolith**.
2. Chỉ tách thành microservices sau khi luồng nghiệp vụ, dữ liệu, bảo mật và test đã ổn định.

Cách làm này giúp phát triển và debug nhanh ở giai đoạn đầu nhưng vẫn giữ ranh giới module đủ rõ để tách service về sau.

## 2. Trạng thái hiện tại

- Giai đoạn hiện tại: `M1 — Identity và RBAC`
- Nhiệm vụ vừa hoàn thành: `M1.6A — Hardening cấu hình, token và cạnh tranh dữ liệu`
- Nhiệm vụ kế tiếp: `M1.7 — Ownership trước mutation` (**chưa bắt đầu**)
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

V1–V4 vẫn giữ nguyên tên bảng cũ trong file migration vì các migration đã chạy không được sửa checksum. V5 đổi tên bằng `ALTER TABLE ... RENAME TO`, nhờ đó database hiện có được nâng cấp mà không xóa hoặc tạo lại dữ liệu. V6–V7 bổ sung và chuẩn hóa RBAC; V8 thêm optimistic locking cho role mà không sửa checksum của migration cũ.

## 6. Danh sách nhiệm vụ

### Quy chuẩn bắt buộc cho mọi nhiệm vụ

Mỗi mục công việc bên dưới là một task contract, không chỉ là tên chức năng. Trước khi viết code, task phải có đủ các nội dung sau; thiếu một mục thì giữ trạng thái chưa sẵn sàng và không bắt đầu triển khai:

1. **Mục tiêu và phạm vi:** vấn đề cần giải quyết, actor/luồng được tác động và phần không thuộc task.
2. **Đầu vào và phụ thuộc:** migration/API/event/config/tài khoản môi trường cần có, module sở hữu dữ liệu và task bắt buộc hoàn thành trước.
3. **Yêu cầu chức năng:** happy path, validation, trạng thái và quy tắc nghiệp vụ phải thực hiện.
4. **Yêu cầu bảo mật và phi chức năng:** authorization/ownership, secret/PII, idempotency, concurrency, timeout, logging, hiệu năng và khả năng vận hành có liên quan.
5. **Đầu ra:** code, migration, API/event contract, cấu hình, tài liệu và test phải được tạo hoặc cập nhật.
6. **Ví dụ kiểm chứng:** tối thiểu một trường hợp thành công, một trường hợp bị từ chối/lỗi và các boundary case quan trọng; ví dụ không được chứa secret hoặc PII thật.
7. **Nghiệm thu:** lệnh kiểm tra, test và hành vi quan sát được; không dùng nhận xét chung như “chạy ổn” hoặc “code đẹp”.
8. **Triển khai và khôi phục:** ảnh hưởng dữ liệu/cấu hình, tương thích ngược, cách rollout và phương án rollback khi task có thay đổi runtime hoặc database.

Quy tắc thực hiện:

- Chỉ làm một task tại một thời điểm. Chỉ chuyển task khi toàn bộ đầu ra và nghiệm thu của task hiện tại đạt.
- Task code phải đi qua `spotless:check`, test phù hợp, Spring Modulith/ArchUnit và migration test nếu có thay đổi database.
- Mọi API phải mô tả request, response, mã lỗi, authorization và ví dụ đã khử dữ liệu nhạy cảm trong OpenAPI.
- Mọi tích hợp ngoài phải tách cấu hình dev/test/prod, dùng secret manager hoặc environment variable, có timeout, audit, health/diagnostic phù hợp và không commit credential.
- Khi yêu cầu còn thiếu hoặc tài liệu tham khảo mâu thuẫn với security baseline, dừng task ở bước phân tích, ghi rõ điểm thiếu/sai và không sao chép nguyên trạng.
- Các task đã đánh dấu `[x]` dùng bằng chứng trong mục tiến độ kiểm chứng làm hồ sơ nghiệm thu; nếu mở lại hoặc thay đổi phạm vi thì phải đặc tả lại theo đủ tám mục trên.

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
- [x] M1.6 Phân hệ quản trị hệ thống: user, role và permission.
  - Yêu cầu chức năng: lọc/phân trang user; xem chi tiết; khóa/mở khóa; gán/bỏ role; xem danh mục permission; tạo/sửa/xóa role tùy biến và cấu hình role-permission. Permission là capability do code sở hữu, không cho tạo chuỗi permission tùy ý qua API.
  - Mô hình quyền: `ADMIN` là cấp cao nhất và là tài khoản duy nhất được quản lý role; `STAFF` được quản lý user trong phạm vi quyền mình đang có; `USER` không được truy cập phân hệ. `ADMIN`, `STAFF`, `USER` là role hệ thống, không được sửa/xóa.
  - Bảo vệ: không gán `ADMIN` qua API; không khóa, vô hiệu hóa hoặc đổi role của tài khoản `ADMIN`; `STAFF` không thể sửa tài khoản `STAFF` khác hay tự nâng quyền; mọi thay đổi status/role/role-permission thu hồi phiên liên quan ngay.
  - Bootstrap: tạo đúng một tài khoản `ADMIN` từ biến môi trường có kiểm tra; không có username/password mặc định và không lưu mật khẩu rõ trong source/database.
  - Đầu ra: feature package `identity.internal.administration`, request/response DTO, service, repository query, permission constants, OpenAPI, Flyway V6-V7 và test H2/MySQL; không trả entity hoặc password hash ra API.
  - Ví dụ và nghiệm thu: STAFF lọc user và gán role hợp lệ thành công; USER nhận `403`; role/permission không tồn tại trả lỗi chuẩn; role hệ thống/tài khoản cao nhất trả `409`; token mang quyền cũ bị từ chối ngay sau mutation; toàn bộ quality gate đạt.
  - Ngoài phạm vi: lịch sử truy cập vẫn thuộc M6.9 vì cần kho append-only, event/outbox, retention và quyền xem dữ liệu nhạy cảm. API tra cứu sau này sẽ nằm trong không gian quản trị hệ thống nhưng không được ghi trực tiếp rải rác từ service M1.6.
- [x] M1.6A Hardening cấu hình, token và cạnh tranh dữ liệu.
  - Mục tiêu/phạm vi: đóng các rủi ro phát hiện khi rà soát M1.6; không thêm nghiệp vụ mới, audit trail, MFA hoặc rate limit phân tán.
  - Đầu vào/phụ thuộc: M1.6, MySQL 8.0.46, Flyway V1–V7 và profile dev/test/prod hiện có.
  - Yêu cầu chức năng: request đăng nhập/token có giới hạn; UUID sai trả contract `400`; refresh/logout vẫn giữ nguyên API và tính idempotent.
  - Bảo mật/phi chức năng: production fail-closed nếu thiếu datasource/JWT/CORS; tài khoản migration tách khỏi runtime; token family được khóa khi rotation/replay; role dùng optimistic lock; MySQL local chỉ bind loopback.
  - Đầu ra: profile `dev`, production/Docker hardening, Flyway V8, validation constants, exception mapping, JaCoCo gate và regression test H2/MySQL.
  - Ví dụ kiểm chứng: token dài hơn 8.192 ký tự bị từ chối; hai refresh đồng thời chỉ một request rotation thành công và replay thu hồi cả family; cập nhật role stale trả conflict.
  - Nghiệm thu: `mvnw.cmd spotless:check clean verify`, `docker compose config`, Flyway/Hibernate trên MySQL 8.0.46 và kiểm tra profile production thiếu secret phải fail-fast.
  - Triển khai/khôi phục: chạy backup trước V8; V8 chỉ thêm cột `version` mặc định `0`. Rollback ứng dụng cần giữ cột thừa vô hại; không sửa/xóa migration đã áp dụng. Trước khi nâng từ đúng V6 lên V7 phải xác nhận chưa tồn tại custom role `STAFF`.
- [ ] M1.7 Kiểm tra ownership trước khi ghi dữ liệu; không dùng `@PostAuthorize` cho update.
  - Yêu cầu: mọi lệnh sửa/xóa dữ liệu người dùng kiểm tra actor, ownership và quyền quản trị trước khi mutation; chốt chính sách trả `403` hoặc `404` để không rò sự tồn tại tài nguyên.
  - Đầu ra: ownership policy dùng lại được ở service/repository predicate, error code thống nhất và test chéo tài khoản; không kiểm tra quyền sau khi dữ liệu đã bị ghi.
  - Ví dụ và nghiệm thu: chủ sở hữu cập nhật được hồ sơ của mình, tài khoản A không sửa được tài khoản B, ADMIN làm được đúng phạm vi; test chứng minh database không đổi khi request bị từ chối.
- [ ] M1.8 Unit, repository, controller và security integration test.
  - Yêu cầu: lập ma trận test cho register, token, introspect, refresh, logout, CAPTCHA, rate limit, my-info, profile, password và admin RBAC; bao phủ happy path, validation, unauthorized, forbidden, replay và concurrency quan trọng.
  - Đầu ra: test độc lập, dữ liệu fixture/builder dễ đọc và báo cáo JaCoCo dùng để phát hiện vùng chưa kiểm chứng; không viết test chỉ để tăng phần trăm coverage.
  - Ví dụ và nghiệm thu: chạy `mvnw.cmd clean verify` với MySQL test thành công, không test flaky, Spring Modulith/ArchUnit đạt và mọi lỗi bảo mật đã biết trong M1 có regression test.

Tiêu chí hoàn thành M1:

- USER không thể tự cấp STAFF/ADMIN hoặc sửa dữ liệu người khác.
- API quản trị áp dụng permission tường minh; chỉ `ADMIN` quản lý role, còn `STAFF` không thể cấp quyền cao hơn quyền đang có.
- Access token và refresh token tách biệt, có rotation/revoke test.
- Trước production, JWT key local/dev phải được thay bằng environment/secret riêng.

### M2 — Catalog và hình ảnh

- [ ] M2.1 Category, Product, SKU/variant và migration.
  - Yêu cầu: định nghĩa aggregate ownership, quan hệ category-product-variant, trạng thái publish, SKU duy nhất, giá `BigDecimal` + currency và audit fields; chốt quy tắc xóa mềm trước khi tạo schema.
  - Đầu ra: entity, enum, repository, Flyway migration và mapping tên bảng/cột tường minh trong package Catalog.
  - Ví dụ và nghiệm thu: không tạo được SKU trùng, giá âm hoặc product không có category hợp lệ; migration chạy trên schema rỗng/schema hiện có và repository test đạt.
- [ ] M2.2 CRUD sản phẩm với DTO, validation và phân trang.
  - Yêu cầu: API tạo/xem/sửa/ẩn sản phẩm và variant dùng request/response DTO, validation theo trạng thái, optimistic locking và sort/page có allowlist; không binding trực tiếp entity.
  - Đầu ra: controller, mapper, service, error code, OpenAPI và query phân trang ổn định.
  - Ví dụ và nghiệm thu: tạo product hợp lệ trả response chuẩn; input sai trả đúng field error; version cũ trả conflict; page/sort bất hợp lệ không gây query tùy ý.
- [ ] M2.3 Upload nhiều ảnh qua abstraction `ObjectStorage`.
  - Yêu cầu: upload nhiều ảnh theo giới hạn số lượng/kích thước/type, kiểm tra magic bytes, sinh object key không đoán được và hỗ trợ ảnh đại diện/thứ tự; business service chỉ gọi abstraction.
  - Đầu ra: `ObjectStorage` port, upload service, metadata entity/DTO, cleanup khi transaction thất bại và fake storage cho test.
  - Ví dụ và nghiệm thu: JPEG/PNG hợp lệ được lưu; file giả MIME, quá lớn hoặc vượt số lượng bị từ chối; lỗi giữa chừng không để metadata/object mồ côi.
- [ ] M2.4 MinIO cho local; lưu metadata/URL thay vì Base64.
  - Yêu cầu: cấu hình bucket/policy/endpoint theo profile, health check, presigned URL có TTL khi cần; không lưu binary/Base64 trong MySQL hay response JSON thông thường.
  - Đầu ra: MinIO adapter, Docker Compose local, typed properties có validation và hướng dẫn khởi tạo bucket.
  - Ví dụ và nghiệm thu: upload/download/delete chạy với local MinIO; restart không mất dữ liệu volume; thiếu credential production làm ứng dụng fail-fast và secret không nằm trong Git.
- [ ] M2.5 Tìm kiếm bằng query/index trước; chỉ dùng stored procedure sau benchmark.
  - Yêu cầu: xác định trường tìm kiếm/filter/sort, chuẩn hóa keyword và index dựa trên query plan; dùng query repository rõ ràng trước khi cân nhắc stored procedure.
  - Đầu ra: search request/response, query/index migration, benchmark dataset/kịch bản và tài liệu quyết định kỹ thuật.
  - Ví dụ và nghiệm thu: tìm theo tên/SKU/category và filter trạng thái cho kết quả ổn định; query không full-scan ngoài ngưỡng đã chốt; ký tự đặc biệt không phá truy vấn.
- [ ] M2.6 Admin authorization, test file type/size và integration test.
  - Yêu cầu: chỉ actor có quyền Catalog phù hợp được mutation; API đọc công khai chỉ trả product đã publish; test cả ownership shop/seller nếu catalog đa shop.
  - Đầu ra: permission constants/policy, security annotations/service guard và bộ unit, controller, storage, repository, integration test.
  - Ví dụ và nghiệm thu: khách xem được hàng publish, không xem draft; USER mutation nhận `403`; file độc hại/quá lớn bị chặn; `clean verify` và test MinIO/MySQL đạt.

Tiêu chí hoàn thành M2:

- Giá dùng `BigDecimal` kèm currency.
- SKU là đơn vị bán và là khóa tham chiếu của Inventory.
- File được kiểm tra kích thước/nội dung và tên lưu trữ không trùng.

### M3 — Inventory

- [ ] M3.1 Stock item, stock movement và stock reservation.
  - Yêu cầu: mô hình tồn theo SKU/location với `on_hand`, `reserved`, `available`; movement là sổ append-only và reservation có trạng thái/TTL rõ ràng.
  - Đầu ra: aggregate, entity, migration, repository, invariant và event contract; Catalog chỉ được tham chiếu bằng SKU/id, không truy cập bảng Catalog.
  - Ví dụ và nghiệm thu: nhập/xuất/reserve tạo movement cân bằng; quantity không âm; migration/repository/domain test đạt.
- [ ] M3.2 Reserve tồn kho bằng atomic conditional update/locking.
  - Yêu cầu: chọn atomic update hoặc locking dựa trên MySQL, kiểm tra available và cập nhật trong một transaction; xác định timeout/deadlock retry có giới hạn.
  - Đầu ra: reservation service/repository query, transaction boundary, metrics conflict và tài liệu chiến lược locking.
  - Ví dụ và nghiệm thu: hai request tranh SKU cuối chỉ một request thành công; không oversell, không lost update và deadlock không retry vô hạn.
- [ ] M3.3 Confirm, release và expiration job.
  - Yêu cầu: state machine cho `RESERVED`, `CONFIRMED`, `RELEASED`, `EXPIRED`; scheduled job dùng UTC, batch có giới hạn và an toàn khi nhiều instance chạy.
  - Đầu ra: command service, expiration job, distributed/DB lock phù hợp, events và cấu hình batch/schedule.
  - Ví dụ và nghiệm thu: confirm không release lại; reservation quá hạn được trả kho đúng một lần; job chạy đồng thời/restart vẫn idempotent.
- [ ] M3.4 Idempotency theo `reservationId`.
  - Yêu cầu: cùng key và cùng payload trả cùng kết quả; cùng key khác payload trả conflict; lưu fingerprint/result/status đủ để retry sau timeout.
  - Đầu ra: unique constraint, idempotency record/policy và response replay contract.
  - Ví dụ và nghiệm thu: gửi reserve/confirm/release lặp không đổi kho lần hai; conflict payload trả `409`; test crash/retry đạt.
- [ ] M3.5 Concurrency test chứng minh không oversell.
  - Yêu cầu: test nhiều thread/transaction trên MySQL thật với barrier đồng bộ, không dùng H2 để kết luận locking; đo success/failure và tổng quantity.
  - Đầu ra: repeatable concurrency test, seed data và báo cáo invariant trước/sau.
  - Ví dụ và nghiệm thu: tổng confirmed + reserved không vượt on-hand qua nhiều lần chạy; không flaky và không bỏ qua exception trong worker.

Tiêu chí hoàn thành M3:

- Không thể reserve nhiều hơn `available_quantity`.
- Request gửi lặp không trừ kho hai lần.
- Reservation hết hạn được release an toàn.

### M4 — Cart, Checkout và Order

- [ ] M4.1 Cart và cart item.
  - Yêu cầu: mỗi user có cart hoạt động theo phạm vi shop đã chốt; add/update/remove/clear kiểm tra SKU tồn tại, quantity và ownership; cart không phải nguồn giá tin cậy.
  - Đầu ra: entity/migration, DTO, service, controller, repository và error contract.
  - Ví dụ và nghiệm thu: thêm cùng SKU gộp quantity theo rule; quantity 0/âm hoặc cart người khác bị từ chối; concurrent update không làm mất item.
- [ ] M4.2 Order state machine và quy tắc chuyển trạng thái.
  - Yêu cầu: liệt kê trạng thái và transition được phép theo actor/event; mọi đổi trạng thái qua một domain method và có version chống ghi đè.
  - Đầu ra: enum/state machine, transition table, domain errors và audit/domain events.
  - Ví dụ và nghiệm thu: `PENDING -> PAID` hợp lệ theo rule; `CANCELLED -> PAID` bị từ chối; unit test bao phủ toàn bộ ma trận transition.
- [ ] M4.3 Checkout tính giá hoàn toàn ở server.
  - Yêu cầu: bỏ qua giá/tổng client gửi lên; đọc catalog/promotion/tax authoritative, kiểm tra availability và dùng rounding/currency policy thống nhất.
  - Đầu ra: pricing service, money value object, checkout quote/command DTO và breakdown response.
  - Ví dụ và nghiệm thu: client sửa giá không ảnh hưởng total; giá thay đổi/hết hàng được báo rõ; tổng line, discount, tax và grand total reconcile chính xác.
- [ ] M4.4 Order item lưu snapshot tên, SKU, giá, giảm giá, thuế và currency.
  - Yêu cầu: snapshot đủ để hiển thị/hạch toán đơn cũ mà không truy cập Catalog; immutable sau khi tạo trừ trường được quy định rõ.
  - Đầu ra: order/order-item migration và mapper từ pricing result sang snapshot.
  - Ví dụ và nghiệm thu: đổi tên/giá/xóa mềm product không đổi đơn cũ; test reload order từ database cho cùng snapshot.
- [ ] M4.5 Điều phối reserve/release inventory bằng domain event.
  - Yêu cầu: Order không gọi repository Inventory; định nghĩa event/command contract và xử lý success/failure/timeout với correlation/business event ID.
  - Đầu ra: publisher/listener, orchestration state và compensation event; chuẩn bị tương thích outbox M6.1.
  - Ví dụ và nghiệm thu: reserve thành công đưa order sang bước kế; reserve thất bại không tạo order hoàn tất; event lặp không trừ/trả kho hai lần.
- [ ] M4.6 Idempotency key cho checkout.
  - Yêu cầu: key gắn user + operation, có fingerprint request, TTL/retention và trạng thái in-progress/completed/failed; chống hai request đồng thời cùng key.
  - Đầu ra: idempotency entity/migration/service và response replay.
  - Ví dụ và nghiệm thu: retry cùng key/payload trả cùng order; khác payload trả `409`; timeout client không tạo đơn thứ hai.
- [ ] M4.7 Test lỗi giữa chừng và compensation.
  - Yêu cầu: xác định failure point sau tạo order, reserve và trước/sau payment; mỗi lỗi có state cuối, retry và compensation rõ ràng.
  - Đầu ra: integration/E2E failure-injection tests và runbook xử lý order mắc kẹt.
  - Ví dụ và nghiệm thu: lỗi sau reserve phải release hoặc được reconciliation xử lý; không có order `PAID` thiếu payment hay tồn kho không giải phóng không có cảnh báo.

Tiêu chí hoàn thành M4:

- Thay đổi giá sản phẩm không làm thay đổi đơn cũ.
- Không sinh hai đơn khi client retry cùng idempotency key.
- Mọi trạng thái order đều đi qua state machine hợp lệ.

### M5 — Payment

- [ ] M5.1 Payment aggregate và provider interface.
  - Yêu cầu: mô hình payment attempt, amount/currency, provider reference, status và transition; Order chỉ phụ thuộc payment port/event, không phụ thuộc SDK cụ thể.
  - Đầu ra: aggregate/entity/migration, provider port, DTO/event và error taxonomy retryable/non-retryable.
  - Ví dụ và nghiệm thu: amount phải khớp order; transition lùi hoặc currency sai bị chặn; domain/repository test đạt.
- [ ] M5.2 Fake payment provider để hoàn chỉnh luồng trước.
  - Yêu cầu: fake provider mô phỏng success, decline, timeout, pending và duplicate callback có thể cấu hình; tuyệt đối không được bật ở production.
  - Đầu ra: adapter/profile local-test, deterministic test controls và sample flow trong OpenAPI/test.
  - Ví dụ và nghiệm thu: từng mode đưa order/payment về trạng thái dự kiến; production profile fail-fast nếu fake provider được chọn.
- [ ] M5.3 Tích hợp provider thật bằng hosted checkout/token; không lưu dữ liệu thẻ.
  - Yêu cầu: dùng hosted page/tokenization, credential từ secret store, TLS, timeout/circuit breaker và redirect allowlist; hoàn thành PCI scope review trước go-live.
  - Đầu ra: provider adapter, typed config, request signing/authentication, return/cancel handler và sanitized operational docs.
  - Ví dụ và nghiệm thu: tạo session và return hợp lệ hoạt động trên sandbox; URL giả, amount mismatch, timeout và provider error được xử lý; database/log không có PAN/CVV.
- [ ] M5.4 Xác minh chữ ký webhook và chống webhook lặp.
  - Yêu cầu: đọc raw body đúng định dạng provider, kiểm tra signature/timestamp/replay window trước parse nghiệp vụ và deduplicate bằng provider event ID.
  - Đầu ra: webhook endpoint, signature verifier, inbox/dedup persistence và audit/metrics.
  - Ví dụ và nghiệm thu: chữ ký đúng xử lý một lần; chữ ký sai/cũ trả lỗi không side effect; cùng event gửi lặp không cập nhật lần hai.
- [ ] M5.5 Payment success/failure/refund cập nhật Order qua event.
  - Yêu cầu: định nghĩa versioned events và mapping state; xử lý event đến trễ/sai thứ tự, partial/full refund theo phạm vi đã chốt.
  - Đầu ra: publisher/consumer, order transition handlers và compensation inventory tương ứng.
  - Ví dụ và nghiệm thu: success confirm order/kho đúng một lần; failure release; refund cập nhật tổng và trạng thái hợp lệ; duplicate/out-of-order tests đạt.
- [ ] M5.6 Reconciliation job cho trạng thái không chắc chắn.
  - Yêu cầu: quét payment pending/unknown theo batch, gọi provider bằng rate limit/timeout, backoff và lock nhiều instance; không tự suy diễn success khi provider không xác nhận.
  - Đầu ra: scheduled job, checkpoint/metrics/alert và operator runbook.
  - Ví dụ và nghiệm thu: pending chuyển đúng theo provider; provider unavailable giữ trạng thái an toàn và retry sau; job lặp không tạo refund/confirm trùng.

Tiêu chí hoàn thành M5:

- Webhook lặp không tạo side effect lặp.
- Payment thành công mới confirm kho; thất bại/timeout phải release kho.
- Có audit trail cho mọi lần thử thanh toán.

### M6 — Hoàn thiện monolith

- [ ] M6.1 Transactional event/outbox trong monolith.
  - Yêu cầu: ghi state và outbox event cùng transaction, claim/publish theo batch an toàn đa instance, retry/backoff, idempotent consumer và retention.
  - Đầu ra: outbox schema/entity, dispatcher, event envelope/versioning, metrics và cleanup job.
  - Ví dụ và nghiệm thu: rollback không phát event; crash sau commit được publish lại; publish/consume lặp không tạo side effect; integration test MySQL đạt.
- [~] M6.2 OpenAPI và chiến lược versioning API.
  - [x] M6.2A Cấu hình nền Swagger UI/OpenAPI, Bearer JWT, security allowlist, production opt-in và integration test.
  - [x] M6.2A.1 Ngoại hóa metadata, contact và server URL bằng typed properties có validation; không áp dụng JWT toàn cục lên API công khai.
  - [ ] M6.2B Hoàn thiện mô tả operation/schema/response cho toàn bộ API sau khi các module nghiệp vụ được xây dựng.
    - Yêu cầu: mỗi operation có summary, authorization, request/response/error schema, pagination/idempotency header và ví dụ đã khử dữ liệu; chốt quy tắc `/api/v1` cùng deprecation.
    - Đầu ra: OpenAPI hoàn chỉnh, generated contract snapshot/diff trong CI và hướng dẫn consumer.
    - Ví dụ và nghiệm thu: public auth không hiển thị yêu cầu Bearer, endpoint bảo mật có scheme đúng; schema/runtime response khớp qua contract test và không có undocumented `5xx` do input.
- [ ] M6.3 Metrics, structured log và tracing.
  - Yêu cầu: correlation/trace ID xuyên request-event-job, log JSON có redaction; RED/USE metrics cho auth, checkout, inventory, payment/outbox và trace không chứa secret/PII.
  - Đầu ra: Micrometer/OpenTelemetry config, dashboards/alerts, log field contract và sampling policy.
  - Ví dụ và nghiệm thu: từ `correlation_id` truy được request tới event; test/log scan không thấy token/password; alert kích hoạt với error/latency giả lập.
- [ ] M6.4 Rate limiting phân tán tại gateway/edge cho checkout và các API tốn tài nguyên; login đã có lớp bảo vệ trong ứng dụng từ M1.4B.
  - Yêu cầu: định nghĩa key theo IP/user/shop/API, quota/burst, `Retry-After`, trusted proxy và fail-open/fail-closed theo endpoint; edge limit không thay authorization.
  - Đầu ra: gateway/WAF/Redis-backed config, dashboard và runbook điều chỉnh quota.
  - Ví dụ và nghiệm thu: vượt quota trả `429`; nhiều instance dùng cùng counter; spoof header không đổi key; checkout/payment không bị retry tự động gây side effect.
- [ ] M6.5 Backup/restore và migration test.
  - Yêu cầu: chốt RPO/RTO, mã hóa backup, quyền truy cập, retention, restore drill và forward-only migration/rollback strategy.
  - Đầu ra: script/runbook, automated migration test từ version hỗ trợ và bằng chứng restore vào môi trường cô lập.
  - Ví dụ và nghiệm thu: restore khôi phục đủ schema/dữ liệu/checksum; migration failure không để schema nửa vời; secret/backup không commit vào repo.
- [ ] M6.6 End-to-end test toàn bộ purchase flow.
  - Yêu cầu: test register/login, catalog, cart, checkout, reserve, payment success/failure, order history và logout trên stack gần production; dữ liệu test cô lập/lặp lại được.
  - Đầu ra: E2E suite, fixtures, environment bootstrap và artifact chẩn đoán đã redacted.
  - Ví dụ và nghiệm thu: happy path hoàn tất; payment fail release kho; retry không tạo đơn/charge trùng; suite chạy ổn định trong CI.
- [ ] M6.7 Load test các điểm login, search, checkout và reserve stock.
  - Yêu cầu: xác định workload, concurrency, dataset, warm-up, p95/p99/error budget và giới hạn tài nguyên; không chạy vào production khi chưa được phép.
  - Đầu ra: versioned load scripts, baseline report, bottleneck/query plan và capacity recommendation.
  - Ví dụ và nghiệm thu: đạt SLO đã chốt không oversell/mất event; kết quả có CPU/RAM/DB pool/GC thay vì chỉ requests-per-second.
- [ ] M6.8 Threat review và dependency/security scan.
  - Yêu cầu: cập nhật data-flow/threat model cho auth, upload, checkout, payment, admin và audit; scan dependency/container/secret/SAST, triage theo mức độ và exploitability.
  - Đầu ra: threat register, mitigation owner/deadline, SBOM và policy CI chặn mức nghiêm trọng đã chốt.
  - Ví dụ và nghiệm thu: không còn finding Critical/High chưa chấp nhận có thời hạn; kiểm thử IDOR, SSRF, upload, webhook replay, JWT, CORS và secret leakage đạt.
- [ ] M6.9 Audit trail và lịch sử truy cập.

Trình tự thực hiện M6.9 — chỉ chuyển sang bước sau khi bước hiện tại đạt điều kiện nghiệm thu:

- [ ] M6.9A Chốt phạm vi và danh mục sự kiện.
  - Yêu cầu: tách access log kỹ thuật khỏi audit event; lập ma trận `action_code` gồm điểm phát sinh, actor, resource, outcome, mức nhạy cảm, thời hạn lưu và module sở hữu.
  - Đầu ra: tài liệu danh mục cho các nhóm authentication, user/permission, product, order, inventory, payment, export và system job; request đọc thông thường không được ghi vào database nếu không truy cập dữ liệu nhạy cảm.
  - Nghiệm thu: mọi sự kiện đều có mã ổn định và lý do cần audit; không còn trường hợp dùng nhãn hiển thị làm định danh sự kiện.
- [ ] M6.9B Tạo cấu trúc module và hợp đồng dữ liệu.
  - Yêu cầu: tạo package `audit` tách rõ `controller`, `dto.request`, `dto.response`, `entity`, `repository`, `service`, `event` và `config`; định nghĩa enum actor/outcome cùng DTO/event bất biến.
  - Đầu ra: hợp đồng dữ liệu bên dưới được version hóa; module nghiệp vụ chỉ phụ thuộc cổng phát sự kiện, không gọi audit repository trực tiếp.
  - Nghiệm thu: compile thành công, Spring Modulith và ArchUnit không báo vi phạm phụ thuộc module.
- [ ] M6.9C Tạo schema lưu trữ append-only.
  - Yêu cầu: thêm Flyway migration, entity và repository cho `he_thong_lich_su_truy_cap`; dùng UUID, `Instant` UTC, giới hạn độ dài cột/JSON và các index đã xác định.
  - Đầu ra: migration chạy được trên schema rỗng và schema hiện có; tầng ứng dụng không cung cấp thao tác update/delete bản ghi audit.
  - Nghiệm thu: migration test, repository test và kiểm tra unique/idempotency đều đạt; tên bảng/cột được khai báo tường minh.
- [ ] M6.9D Chuẩn hóa ngữ cảnh request và bảo vệ dữ liệu.
  - Yêu cầu: tạo resolver dùng `Clock`, correlation ID, user-agent và IP; chỉ đọc proxy header từ trusted proxy; tạo allowlist/redactor cho `detail_json`.
  - Đầu ra: actor snapshot và request metadata được thu thập tại một nơi dùng chung, không sao chép logic trong từng service.
  - Nghiệm thu: test xác nhận không giả mạo được IP qua `X-Forwarded-For` từ nguồn không tin cậy và password/token/cookie/CAPTCHA answer luôn bị loại bỏ.
- [ ] M6.9E Xây luồng ghi sự kiện tin cậy.
  - Yêu cầu: tạo cổng phát audit event, writer và cơ chế transaction/outbox; dùng `business_event_id` làm khóa chống ghi trùng khi consumer retry.
  - Đầu ra: business service phát sự kiện có kiểu thay vì tự dựng entity; quy tắc xử lý lỗi ghi audit được tài liệu hóa riêng cho security event và business event.
  - Nghiệm thu: integration test chứng minh commit tạo đúng một audit record, rollback không tạo bản ghi sai và retry không tạo bản ghi trùng.
- [ ] M6.9F Gắn audit vào authentication và quản trị tài khoản.
  - Yêu cầu: phát sự kiện cho login success/failure/denied, logout, refresh/revoke, đổi/reset mật khẩu, cập nhật hồ sơ và thay đổi role/permission; login thất bại cho phép `actor_user_id = null`.
  - Đầu ra: mỗi luồng có `outcome`, HTTP metadata và lý do thất bại đã chuẩn hóa nhưng không lộ thông tin giúp dò tài khoản.
  - Nghiệm thu: test API xác nhận từng luồng tạo đúng sự kiện, token đã logout/revoke không được chấp nhận và log không chứa credential/token thô.
- [ ] M6.9G Gắn audit vào nghiệp vụ và tác vụ hệ thống.
  - Yêu cầu: phát sự kiện cho thay đổi trạng thái quan trọng của Product, Order, Inventory và Payment; scheduled job dùng `actor_type = SYSTEM`.
  - Đầu ra: mỗi event có `resource_type`, `resource_id` và snapshot/diff tối thiểu có schema/version; không tạo foreign key xuyên module.
  - Nghiệm thu: test timeline của resource đúng thứ tự và tác vụ hệ thống không cần giả lập người dùng đăng nhập.
- [ ] M6.9H Xây API tra cứu có phân quyền.
  - Yêu cầu: thêm quyền riêng `AUDIT_READ`; xây API lọc theo actor, action, module, outcome, IP, correlation ID, resource và khoảng thời gian UTC; hỗ trợ timeline resource và cursor/keyset pagination.
  - Đầu ra: platform admin xem theo phạm vi được cấp, shop/seller chỉ xem dữ liệu thuộc shop của mình; không có API sửa/xóa audit.
  - Nghiệm thu: test trả `403` khi thiếu quyền, không rò dữ liệu chéo shop/seller, khoảng ngày dùng `[from, to)` và kết quả sắp xếp ổn định theo `occurred_at`, `id`.
- [ ] M6.9I Xây xuất dữ liệu và chính sách vòng đời.
  - Yêu cầu: thêm quyền `AUDIT_EXPORT`, giới hạn khoảng ngày/số lượng và xuất bằng streaming hoặc job bất đồng bộ; cấu hình retention/archive và cleanup theo batch.
  - Đầu ra: không tải toàn bộ dữ liệu vào RAM, không trả file Base64 trong JSON; chính thao tác yêu cầu/tải bản xuất cũng được audit.
  - Nghiệm thu: test giới hạn export, quyền truy cập file, thời hạn file, cleanup/archive và tải lớn không làm cạn heap.
- [ ] M6.9J Hoàn tất quality gate và tài liệu vận hành.
  - Yêu cầu: chạy Spotless, unit test, integration test, Modulith, ArchUnit, migration test và security test; cập nhật OpenAPI, retention, quyền xem IP/user-agent và runbook điều tra sự cố.
  - Đầu ra: báo cáo kiểm thử và ví dụ truy vết hoàn chỉnh từ `correlation_id` hoặc resource tới audit record và structured log.
  - Nghiệm thu: toàn bộ tiêu chí M6.9 bên dưới đạt, không còn lỗi đã biết mức cao/nghiêm trọng; chỉ khi đó mới đánh dấu M6.9 hoàn thành và chuyển nhiệm vụ.

Hợp đồng dữ liệu dự kiến cho M6.9:

| Nhóm | Trường chính | Quy tắc |
| --- | --- | --- |
| Chủ thể | `actor_type`, `actor_user_id`, `actor_username_snapshot`, `shop_id` | `actor_type` tối thiểu gồm `USER`, `SYSTEM`; `actor_user_id` dùng UUID và được phép `null` cho đăng nhập thất bại hoặc tác vụ hệ thống; snapshot giúp tra cứu sau khi tài khoản đổi tên hoặc bị xóa. |
| Hành động | `action_code`, `feature`, `module`, `outcome` | Dùng mã ổn định thay vì chỉ lưu nhãn hiển thị; `outcome` tối thiểu gồm `SUCCESS`, `FAILURE`, `DENIED`. |
| Thời điểm/request | `occurred_at`, `ip_address`, `user_agent`, `correlation_id`, `http_method`, `request_path`, `http_status` | Lưu UTC; `correlation_id` liên kết với structured log và distributed trace. Chỉ tin proxy header khi request đến từ trusted proxy. |
| Đối tượng nghiệp vụ | `resource_type`, `resource_id`, `business_event_id` | Cho phép truy vết User, Product, Order, Inventory và Payment mà không tạo foreign key xuyên module/service. |
| Chi tiết | `detail_schema`, `detail_json` | Snapshot/diff tùy module phải có schema/version, chỉ lưu metadata đã allowlist và giới hạn kích thước; cấm password, token, signing key, CAPTCHA answer, cookie và request/response body thô. |

Tiêu chí hoàn thành M6.9:

- Access log dung lượng lớn đi vào centralized logging với retention ngắn; database chỉ giữ audit event có giá trị bảo mật/nghiệp vụ hoặc lượt đọc dữ liệu nhạy cảm.
- Audit record là bất biến ở tầng ứng dụng; thao tác quản trị phải được phân quyền riêng và chính thao tác xuất lịch sử cũng được audit.
- Có index tối thiểu theo `(actor_user_id, occurred_at)`, `(module, occurred_at)`, `correlation_id` và `(resource_type, resource_id, occurred_at)`; mọi API dùng cursor/keyset pagination hoặc giới hạn phân trang rõ ràng.
- Truy vấn thời gian dùng khoảng nửa mở `[from, to)` theo UTC, giới hạn độ dài khoảng tìm kiếm và sắp xếp ổn định theo `occurred_at` cùng `id`.
- Integration test bao phủ actor `SYSTEM`, đăng nhập thất bại, data scope giữa các shop/seller, timeline theo resource, redaction dữ liệu nhạy cảm, idempotency/outbox và giới hạn xuất dữ liệu.
- Chính sách retention, archive và quyền xem IP/user-agent được tài liệu hóa; test xác nhận không có secret/PII ngoài allowlist trong `detail_json`.

Điều kiện được phép bắt đầu tách microservices:

- Luồng đăng ký → đăng nhập → chọn sản phẩm → checkout → reserve → payment → hoàn tất chạy ổn định.
- Module verification, unit test, integration test và E2E đều đạt.
- API/event contract đã version hóa.
- Không module nào truy cập repository hoặc bảng thuộc module khác.
- Có idempotency, outbox, audit trail, metrics, trace ID và quy trình rollback migration.

### M7 — Tách microservices

- [ ] M7.1 Thêm API Gateway; monolith vẫn chạy phía sau gateway.
  - Yêu cầu: route/version/auth propagation, CORS, rate limit, request size, timeout và correlation ID được cấu hình tập trung; gateway không chứa nghiệp vụ.
  - Đầu ra: gateway service/config, local compose, health/metrics và migration plan giữ monolith hoạt động.
  - Ví dụ và nghiệm thu: route public/protected đúng, header giả bị loại, timeout/`429` chuẩn; có thể tắt gateway và rollback traffic về monolith.
- [ ] M7.2 Tách Identity Service và chuyển các service sang xác minh JWT bằng public key/JWKS.
  - Yêu cầu: xác định ownership user/role/token, issuer/audience/key rotation/JWKS cache; service khác không gọi Identity cho mọi request và không dùng shared signing secret.
  - Đầu ra: Identity deployable, JWKS endpoint/client config, data migration/dual-run và contract tests.
  - Ví dụ và nghiệm thu: key rotation không downtime; token issuer/audience sai hoặc revoked theo policy bị từ chối; rollback không mất account/session hợp lệ.
- [ ] M7.3 Tách Catalog Service và object storage.
  - Yêu cầu: Catalog sở hữu product/category/media schema và storage credential; consumer dùng API/event versioned, không truy cập bảng trực tiếp.
  - Đầu ra: service/database pipeline, migration/sync/cutover plan và backward-compatible contracts.
  - Ví dụ và nghiệm thu: đọc/ghi catalog qua service mới, media vẫn truy cập được, dual-write/replay không tạo SKU trùng và rollback đã thử.
- [ ] M7.4 Tách Inventory Service; thay lời gọi trong tiến trình bằng contract/event.
  - Yêu cầu: Inventory sở hữu stock/reservation, reserve/confirm/release có idempotency, timeout và consistency model rõ; Order không dùng repository Inventory.
  - Đầu ra: sync/async contracts, outbox/inbox, data migration và reconciliation.
  - Ví dụ và nghiệm thu: network timeout/retry không oversell; event lặp/sai thứ tự an toàn; invariant kho giữ đúng trong cutover.
- [ ] M7.5 Tách Payment Service và webhook endpoint.
  - Yêu cầu: Payment sở hữu provider credential/webhook/payment data; signature verification ở boundary, network/PCI scope và callback routing được chốt.
  - Đầu ra: isolated service/database/secret, public webhook route, event contract và cutover/runbook.
  - Ví dụ và nghiệm thu: duplicate webhook/timeout an toàn; không lộ credential/PAN; reconciliation so khớp trước và sau cutover.
- [ ] M7.6 Tách Order Service cuối cùng vì đây là module điều phối nhiều nhất.
  - Yêu cầu: chỉ tách sau khi Catalog/Inventory/Payment contract ổn định; Order giữ snapshot, saga/orchestration, idempotency và compensation rõ ràng.
  - Đầu ra: Order service/database, migration, event state machine và traffic cutover theo phase.
  - Ví dụ và nghiệm thu: purchase flow E2E đạt khi service lỗi/chậm; không mất/nhân đôi order; rollback traffic/data có bằng chứng.
- [ ] M7.7 Mỗi service có database/schema, pipeline và health check riêng.
  - Yêu cầu: cấm foreign key/query xuyên service, credential tối thiểu quyền, readiness khác liveness, migration độc lập và pipeline có security/contract gates.
  - Đầu ra: database/schema ownership matrix, CI/CD template, deployment/rollback và backup policy từng service.
  - Ví dụ và nghiệm thu: dừng dependency làm readiness phản ánh đúng mà không restart loop; service không thể đọc schema ngoài phạm vi bằng credential của nó.
- [ ] M7.8 Broker + transactional outbox/inbox + retry + dead-letter queue.
  - Yêu cầu: chọn broker theo throughput/ordering/retention, version envelope, partition key, at-least-once semantics, idempotent consumer, retry backoff và DLQ ownership.
  - Đầu ra: broker config, outbox/inbox library/contract, schema registry strategy, replay/DLQ runbook và metrics.
  - Ví dụ và nghiệm thu: publish/consume duplicate, poison message, broker outage và replay không gây side effect trùng; DLQ có alert và cách xử lý được thử.
- [ ] M7.9 Distributed tracing, centralized logs và dashboard/SLO.
  - Yêu cầu: trace context qua HTTP/broker/job, log chuẩn hóa/redacted, SLI/SLO/error budget cho từng service và alert theo tác động người dùng.
  - Đầu ra: collector/backend config, dashboards, retention/access policy và incident links từ trace tới log/metric.
  - Ví dụ và nghiệm thu: theo dõi được một checkout qua toàn bộ service; sampling vẫn giữ error trace; dữ liệu quan sát không chứa token/PII cấm.
- [ ] M7.10 Resilience test: timeout, retry, duplicate event và service unavailable.
  - Yêu cầu: fault-injection matrix cho từng dependency, retry budget, circuit breaker, bulkhead, fallback và recovery/reconciliation; không retry mù thao tác không idempotent.
  - Đầu ra: automated resilience/chaos tests, SLO impact report và runbook.
  - Ví dụ và nghiệm thu: service mất kết nối không gây cascading failure; duplicate/out-of-order event an toàn; hệ thống tự phục hồi hoặc phát cảnh báo có hành động rõ ràng.

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
- Profile `prod` bắt buộc truyền `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SIGNER_KEY` và `CORS_ALLOWED_ORIGINS`; không dùng fallback local.
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
- M6.9 hiện chỉ là thiết kế tương lai, chưa tạo entity, migration hoặc API; nhiệm vụ kế tiếp theo thứ tự triển khai là M1.7.
- Đã chuẩn hóa task contract bắt buộc gồm mục tiêu/phạm vi, phụ thuộc, yêu cầu chức năng, bảo mật/phi chức năng, đầu ra, ví dụ, nghiệm thu và rollout/rollback; toàn bộ nhiệm vụ chưa hoàn thành từ M1.7 đến M7.10 đã có yêu cầu, đầu ra và ví dụ nghiệm thu cụ thể.
- Hai tài liệu tích hợp SSO do người dùng cung cấp chỉ được dùng làm ví dụ về cách viết yêu cầu, đầu ra và nghiệm thu; VNeID SSO không thuộc phạm vi Shop và không được thêm vào backlog.
- M1.6 học mô hình user–role–chức năng từ `khcn-sso` và gom toàn bộ use case mới trong feature package `identity.internal.administration`; không sao chép role ID hard-code, mật khẩu mặc định, controller trả `Object` hoặc service quản trị quá lớn.
- Flyway V6 tạo nền RBAC quản trị; Flyway V7 đổi role cũ `SUPER_ADMIN` thành `ADMIN`, đổi role cũ `ADMIN` thành `STAFF`, giữ nguyên các liên kết user/quyền và thu hồi phiên mang claim cũ.
- Tài khoản `ADMIN` duy nhất được bootstrap bằng bốn biến môi trường, mật khẩu BCrypt và không có credential mặc định; API không thể gán role này, khóa tài khoản này hoặc sửa/xóa ba role hệ thống.
- Phân hệ công bố API lọc/phân trang user, xem chi tiết, đổi status/role, xem permission và CRUD role tùy biến; request/response dùng DTO, validation chuẩn và không lộ entity/password hash.
- `STAFF` chỉ gán được role có tập permission không vượt quyền của chính mình; chỉ `ADMIN` quản lý role hoặc tài khoản `STAFF`. `USER` nhận `403` tại toàn bộ endpoint quản trị.
- Thay đổi status, tập role hoặc role-permission thu hồi refresh session liên quan trong cùng transaction; regression test xác nhận access token chứa claim cũ lập tức nhận `401`.
- Test MySQL thật đã phát hiện cú pháp escape chỉ H2 chấp nhận trong bản migration đầu; migration được sửa thành seed tường minh và chạy lại thành công trên cả hai database.
- `mvnw.cmd spotless:check clean verify` sau M1.6: thành công với 63 test, 0 failure, 0 error, 0 skipped; MySQL 8.0.46, Flyway V6, Spring Modulith, ArchUnit, OpenAPI, Spotless và JaCoCo đều đạt.
- Lịch sử truy cập chưa được code trong M1.6; backlog M6.9 tiếp tục sở hữu kho audit append-only và sau này cung cấp API đọc trong không gian quản trị hệ thống.

Ngày 2026-09-28:

- Đã tập trung toàn bộ thông báo lỗi API, validation, security và bootstrap vào `message.properties`; response lỗi và mô tả OpenAPI mặc định đã được Việt hóa, còn mã role/permission, tên header và thuật ngữ kỹ thuật trong log vẫn giữ nguyên để bảo đảm khả năng tích hợp và vận hành.
- Đã chuẩn hóa hệ role thành `ADMIN` cấp cao nhất, `STAFF` quản trị giới hạn và `USER` người dùng thường. Flyway V7 chuyển dữ liệu từ tên role cũ mà không mất liên kết user–role/role–permission và thu hồi các phiên chứa claim cũ.
- Migration V7 đã chạy thành công trên H2 2.3, MySQL 8.0.46 qua Testcontainers và database Docker `shop` tại cổng `3307`; database có đủ 9 bảng `xac_thuc_*`, ba role mới và không còn phiên refresh cũ đang hoạt động.
- `mvnw.cmd spotless:check clean verify` sau chuẩn hóa thông báo và role: thành công với 65 test, 0 failure, 0 error, 0 skipped; Spring Modulith, ArchUnit, OpenAPI, Spotless và JaCoCo đều đạt.
- Database local hiện chưa có tài khoản được gán role `ADMIN`. Đây là trạng thái an toàn có chủ đích: chỉ bật bootstrap một lần sau khi cung cấp bộ `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` riêng, sau đó tắt `ADMIN_BOOTSTRAP_ENABLED`.
- M1.6A tách toàn bộ giá trị local sang profile `dev`; cấu hình nền và image Docker không còn fallback datasource, CORS hoặc JWT key. Image mặc định dùng `prod`, nơi Flyway bắt buộc dùng credential migration riêng với datasource runtime.
- Docker Compose dùng đúng MySQL 8.0.46 như Testcontainers và chỉ publish database trên `127.0.0.1:3307`, tránh vô tình mở MySQL ra mạng LAN.
- Request login giới hạn username/password giống register; request introspect/refresh/logout giới hạn token tối đa 8.192 ký tự để loại payload bất thường trước khi parse JWT.
- Refresh/logout dùng pessimistic lock trên toàn token family. Regression test MySQL chạy hai refresh đồng thời chứng minh chỉ một rotation thành công, request replay thu hồi family và access token vừa cấp không còn hợp lệ.
- Flyway V8 thêm cột `version` cho `xac_thuc_vai_tro`; JPA `@Version` ngăn hai cập nhật role đồng thời âm thầm ghi đè nhau. UUID path sai định dạng trả HTTP `400`/mã `1010` thay vì lỗi `500`.
- JaCoCo đã trở thành quality gate bắt buộc với line coverage tối thiểu 85% và branch coverage tối thiểu 65%, thay vì chỉ sinh báo cáo.
- CI đã nâng các GitHub-maintained action lên runtime hiện hành, chặn pull request bổ sung dependency có lỗ hổng từ mức `moderate` và bật Dependabot hàng tuần cho Maven/GitHub Actions.
- `mvnw.cmd clean verify`: thành công với 69 test, 0 failure, 0 error, 0 skipped; line coverage 89,38% và branch coverage 67,85%, đều vượt ngưỡng bắt buộc.
- Kiểm tra fail-closed xác nhận profile `prod` thoát với mã `1` khi thiếu `SPRING_FLYWAY_URL`, thay vì khởi động nhầm bằng cấu hình local.
- Database Docker local đã được nâng từ V7 lên V8, giữ nguyên 2 user và 2 liên kết user-role; ba role hệ thống đều có `version=0`. Container được bind lại thành `127.0.0.1:3307` và health MySQL đạt. Container cũ dừng tại `mysql-8.0-network-backup-20260928`; bản sao volume độc lập là `shop-mysql-backup-before-local-bind-20260928` để có thể khôi phục.

Toàn bộ M0, M1.1, M1.2, M1.3, M1.4, M1.4A, M1.4B, M1.4C, M1.4D, M1.4E, M1.4F, M1.4G, M1.4H, M1.5, M1.6, M1.6A và M6.2A.1 đã vượt quality gate. Dừng tại đây theo nguyên tắc một nhiệm vụ; M1.7 chưa bắt đầu.

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
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
docker start mysql-8.0
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd spring-boot:run
```

Project mặc định kết nối MySQL tại `localhost:3307`. Nếu chưa có container `mysql-8.0`, có thể dùng `docker compose up -d mysql`; không chạy đồng thời hai container trên cùng port.

Ứng dụng dùng tài khoản MySQL riêng `shop` thay vì tài khoản quản trị `root`. Cấu hình datasource dùng đúng nhóm biến chuẩn của Spring Boot: `SPRING_DATASOURCE_DRIVER`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD`. Mọi giá trị mặc định local chỉ tồn tại trong profile `dev`; chạy không chọn profile và không truyền biến bắt buộc sẽ fail-fast.

Kết nối DataGrip vào MySQL local:

1. Chọn `New` → `Data Source` → `MySQL`.
2. Nhập `Host=localhost`, `Port=3307`, `User=shop`, `Password=shop-local-password`, `Database=shop`.
3. JDBC URL tương ứng là `jdbc:mysql://localhost:3307/shop`.
4. Chọn `Test Connection`, sau đó trong tab `Schemas` đánh dấu schema `shop` và bấm `Apply`.
5. Sau khi ứng dụng chạy, dùng `Synchronize`/`Refresh` trong DataGrip. `flyway_schema_history` phải có V1–V8 và các bảng Identity hiện hành phải mang tiền tố `xac_thuc_`.

Nếu DataGrip vẫn hiển thị `identity_*`, chạy câu lệnh sau để kiểm tra. Kết quả dừng trước V8 nghĩa là container chưa được ứng dụng mới chạy đủ migration, không phải entity vẫn ánh xạ tên tiếng Anh:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

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

Các endpoint Identity đã hoàn thành đến M1.6:

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
GET    /api/system-administration/users                    Lọc và phân trang user
GET    /api/system-administration/users/{userId}           Xem chi tiết user
PATCH  /api/system-administration/users/{userId}/status    Khóa/mở khóa user
PUT    /api/system-administration/users/{userId}/roles     Thay tập role của user
GET    /api/system-administration/roles                    Xem role và permission đã gán
POST   /api/system-administration/roles                    Tạo role tùy biến
PUT    /api/system-administration/roles/{roleCode}         Sửa role tùy biến
DELETE /api/system-administration/roles/{roleCode}         Xóa role tùy biến chưa sử dụng
GET    /api/system-administration/permissions              Xem capability do hệ thống hỗ trợ
```

Ma trận quyền quản trị:

| Chức năng | ADMIN | STAFF | USER |
|---|:---:|:---:|:---:|
| Xem user/role/permission | Có | Có | Không |
| Khóa/mở khóa user thường | Có | Có | Không |
| Đổi role user thường | Có | Có, không vượt quyền hiện có | Không |
| Quản lý tài khoản STAFF | Có | Không | Không |
| Tạo/sửa/xóa role tùy biến | Có | Không | Không |
| Sửa/xóa role hệ thống hoặc tài khoản ADMIN | Không | Không | Không |

Tạo tài khoản cao nhất lần đầu bằng biến môi trường. Chỉ bật bootstrap khi khởi tạo, sau khi đăng nhập thành công phải tắt `ADMIN_BOOTSTRAP_ENABLED` và xóa password khỏi môi trường chạy:

```powershell
$env:ADMIN_BOOTSTRAP_ENABLED="true"
$env:ADMIN_USERNAME="platform-root"
$env:ADMIN_EMAIL="platform-root@example.com"
$env:ADMIN_PASSWORD="replace-with-a-strong-unique-password"
$env:SPRING_PROFILES_ACTIVE="dev"
.\mvnw.cmd spring-boot:run
```

Ứng dụng từ chối khởi tạo thêm nếu đã có một `ADMIN`; nếu database bị can thiệp thành nhiều tài khoản `ADMIN`, startup fail-fast. Role/permission nằm trong access token, vì vậy thay đổi status, role hoặc permission sẽ thu hồi refresh session tương ứng để access token cũ mất hiệu lực ngay.

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
$env:SPRING_DATASOURCE_DRIVER="com.mysql.cj.jdbc.Driver"
$env:SPRING_DATASOURCE_URL="jdbc:mysql://db-host:3306/shop"
$env:SPRING_DATASOURCE_USERNAME="shop"
$env:SPRING_DATASOURCE_PASSWORD="replace-me"
$env:SPRING_FLYWAY_URL="jdbc:mysql://db-host:3306/shop"
$env:SPRING_FLYWAY_USERNAME="shop_migration"
$env:SPRING_FLYWAY_PASSWORD="replace-with-a-separate-migration-secret"
$env:JWT_SIGNER_KEY="replace-with-at-least-64-random-bytes"
$env:CORS_ALLOWED_ORIGINS="https://shop.example.com"
```

`SPRING_DATASOURCE_*` là credential runtime chỉ có quyền DML cần thiết; `SPRING_FLYWAY_*` là credential triển khai có quyền DDL và chỉ dùng để chạy migration. Không dùng tài khoản `root` cho một trong hai nhóm. Trước khi triển khai bản có V7 lên database đang đúng V6, chạy `SELECT code FROM xac_thuc_vai_tro WHERE code = 'STAFF'`; nếu đã có custom role trùng tên thì phải đổi mã role đó có kiểm soát trước khi chạy migration.

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

### ADR-006: Phân quyền quản trị theo capability và một ADMIN cấp cao nhất

Trạng thái: Accepted.

Giữ mô hình user–role–chức năng từ `khcn-sso`, nhưng không sao chép ID role hard-code, mật khẩu mặc định, service quản trị quá lớn hoặc annotation quyền bị comment. Shop dùng permission constant trùng với dữ liệu Flyway, controller kiểm tra bằng `@PreAuthorize` và service tiếp tục bảo vệ invariant quan trọng.

Permission là capability mà code thực sự kiểm tra nên API chỉ cho xem danh mục, không cho tự tạo permission. Role tùy biến được cấu hình từ capability có sẵn. `ADMIN` chỉ được bootstrap từ environment, không thể gán qua API và tài khoản này không thể bị khóa hoặc đổi role; sửa quyền làm thu hồi các phiên đang giữ claim cũ.
