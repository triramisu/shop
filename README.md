# Shop

## 1. Mục tiêu

Xây dựng hệ thống thương mại điện tử bằng Java 21 và Spring Boot theo hai giai đoạn:

1. Hoàn thiện nghiệp vụ dưới dạng **modular monolith**.
2. Chỉ tách thành microservices sau khi luồng nghiệp vụ, dữ liệu, bảo mật và test đã ổn định.

Cách làm này giúp phát triển và debug nhanh ở giai đoạn đầu nhưng vẫn giữ ranh giới module đủ rõ để tách service về sau.

README là tài liệu chính thức được theo dõi bằng Git từ M4.7. Các ghi chú cũ nói README chỉ lưu local phản ánh đúng quy trình lịch sử trước thời điểm này.

## 2. Trạng thái hiện tại

- Giai đoạn hiện tại: `M5 — Payment`.
- Nhiệm vụ vừa hoàn thành: `M5.2 — Fake payment provider để hoàn chỉnh luồng trước`.
- Nhiệm vụ đang thực hiện: `M5.3 — Tích hợp Stripe hosted checkout`; implementation, quality gate local và smoke test Stripe sandbox thật đã đạt, đang chờ required checks của PR trước khi đóng/merge.
- Nhiệm vụ kế tiếp: `M5.4 — Xác minh chữ ký webhook và chống webhook lặp`; chỉ bắt đầu sau khi M5.3 vượt quality gate và được merge.
- Mục tiêu tiến độ: hoàn thành toàn bộ dự án trước ngày `30/10/2026`; Sonar và full regression chỉ chạy khi đóng từng milestone M2–M7, còn mỗi task vẫn phải vượt kiểm thử đúng phạm vi trước khi merge.
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
11. Mọi message trả cho người dùng phải là tiếng Việt và được quản lý tập trung trong `message.properties`; mã kỹ thuật ổn định cho máy vẫn dùng ASCII.

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

### Cổng kiểm chứng dữ liệu cuối mỗi milestone

Trước khi đánh dấu hoàn thành toàn bộ một milestone như M1, M2, M3..., bắt buộc chạy lại cổng dữ liệu trên database thật; unit test hoặc H2 không thể thay thế cổng này. Task có migration vẫn phải kiểm tra database ngay trong task, không chờ đến cuối milestone.

1. Chạy toàn bộ Flyway từ schema rỗng và nâng cấp từ phiên bản cuối của milestone trước; checksum phải hợp lệ và không mất dữ liệu cũ.
2. Đối chiếu bảng, cột, kiểu dữ liệu, khóa chính/ngoại, unique, check constraint và index với mapping/entity cùng contract đã chốt.
3. Nạp bộ dữ liệu deterministic giống dữ liệu thật, gồm happy path, biên, trạng thái không hợp lệ và quan hệ nhiều bản ghi; không dùng dữ liệu production hoặc thông tin nhạy cảm thật. Bảng nghiệp vụ chính có tối thiểu 500 bản ghi khi phù hợp về ngữ nghĩa; bảng danh mục hệ thống nhỏ phải dùng bộ giá trị đầy đủ, không nhân bản giả để đủ số lượng.
4. Chạy end-to-end từ API qua service, database và provider fake/sandbox; kiểm tra CRUD/nghiệp vụ, phân quyền, transaction rollback, idempotency và cạnh tranh dữ liệu quan trọng trên MySQL thật.
5. Chạy truy vấn kiểm tra invariant: không có bản ghi mồ côi, liên kết sai, khóa trùng, trạng thái bất hợp lệ hoặc dữ liệu phụ còn sót sau rollback/xóa.
6. Xác nhận test có thể chạy lặp lại, fixture được dọn sạch và kết quả phân trang/sắp xếp ổn định.
7. Với migration có rủi ro, kiểm tra backup/restore hoặc phương án rollback/roll-forward đã ghi trong task contract.
8. Chỉ đóng milestone khi full regression, coverage gate, Spring Modulith/ArchUnit, Spotless, Sonar và Docker Compose đều đạt; ghi lại phiên bản MySQL, số migration, số test, coverage và kết quả kiểm tra dữ liệu làm bằng chứng.

### M0 — Nền móng modular monolith

- [x] M0.1 Khởi tạo Maven project, Java 21, Spring Boot và Spring Modulith.
- [x] M0.2 Tạo package cho năm module và architecture test kiểm tra ranh giới.
- [x] M0.3 Cấu hình MySQL, Flyway, profile test và Docker Compose.
- [x] M0.4 Chuẩn hóa response lỗi, logging, correlation ID và health endpoint.
- [x] M0.5 Thiết lập format/check, unit test và CI cơ bản.
- [x] M0.6 Tạo Git baseline đã kiểm chứng, nhánh `develop` và đồng bộ GitHub remote.
- [x] M0.7 Đồng bộ môi trường Maven/Docker sau khi clone.
  - **Mục tiêu và phạm vi:** mọi thành viên dùng cùng Maven repository, Maven Wrapper, Java 21 trong image build và quy tắc line ending; không cài Maven hệ thống, không sao chép credential/Nexus nội bộ và không thay đổi nghiệp vụ ứng dụng.
  - **Đầu vào và phụ thuộc:** repository GitHub, Maven Wrapper đã commit, Docker Desktop, CI hiện có và cấu trúc `ci/settings.xml` của dự án tham khảo; máy chạy Maven trực tiếp vẫn cần JDK 21 cùng `JAVA_HOME` hợp lệ.
  - **Yêu cầu chức năng:** Maven tự đọc cấu hình chung từ `.mvn/maven.config`; dependency/plugin release lấy từ Maven Central qua HTTPS, kiểm tra checksum và không dùng snapshot; Docker build dùng chính wrapper/cấu hình đã commit.
  - **Bảo mật và phi chức năng:** không commit username/password/token; không phụ thuộc private Nexus của dự án tham khảo; line ending phải chạy được trên Windows và Linux; Docker build phải tận dụng cache Maven mà không đưa cache vào image runtime.
  - **Đầu ra:** `ci/settings.xml`, `.mvn/maven.config`, `.gitattributes` và Dockerfile nhiều stage dùng Maven Wrapper.
  - **Ví dụ kiểm chứng:** clone sạch trên Windows đọc được profile `shop-public-repositories`; `mvnw.cmd clean verify` thành công; Docker/Linux thực thi được `./mvnw` và sinh image `shop`.
  - **Nghiệm thu:** effective settings đúng; 69 test, Flyway H2/MySQL, Modulith, ArchUnit, Spotless và JaCoCo đạt; `docker build --check` không cảnh báo; Docker image build thành công; GitHub CI commit merge `e3a9e92` thành công.
  - **Triển khai và khôi phục:** thay đổi chỉ tác động build/dependency resolution, không migration/runtime data; có thể rollback merge `e3a9e92`, nhưng phải khôi phục Dockerfile cũ và bỏ đồng thời `.mvn/maven.config` để tránh cấu hình nửa vời.
- [x] M0.8 Bảo vệ nhánh `main` và bắt buộc quality gate.
  - **Mục tiêu và phạm vi:** ngăn push/force-push/xóa nhánh làm bỏ qua review hoặc CI trên `main`; không thay đổi source code, nghiệp vụ hay dữ liệu ứng dụng.
  - **Đầu vào và phụ thuộc:** quyền admin repository `triramisu/shop`, workflow `CI`, check `verify` đang thành công và quy ước nhánh `feature/<chức-năng>/<phần-sửa>`.
  - **Yêu cầu chức năng:** mọi thay đổi vào `main` đi qua pull request; branch phải cập nhật với `main` trước merge; hai check `verify` và `dependency-review` bắt buộc đạt trạng thái GitHub chấp nhận; conversation phải được resolve trước merge. Repository hiện chỉ có một collaborator nên approval tạm đặt `0`; phải tăng thành `1` ngay sau khi có reviewer thứ hai.
  - **Bảo mật và phi chức năng:** chặn force-push và xóa `main`; áp dụng cho administrator; giữ tên job required duy nhất giữa các workflow để tránh kết quả check mơ hồ. `dependency-review` chạy trên pull request và trạng thái `skipped` ở push được GitHub coi là hợp lệ.
  - **Đầu ra:** branch protection hoặc repository ruleset đang active cho `main`, required checks đúng tên `verify`/`dependency-review` và bằng chứng cấu hình đọc lại từ GitHub API.
  - **Ví dụ kiểm chứng:** pull request có `verify` và `dependency-review` đạt được phép merge theo chính sách; PR có một check đỏ/pending bị chặn; direct push vào `main` bị từ chối bằng `GH006`.
  - **Nghiệm thu:** GitHub báo branch được bảo vệ; API trả protection active; PR thử nghiệm số `6` đi từ `blocked` sang `clean` sau khi hai required checks thành công; PR được đóng không merge và nhánh probe được xóa.
  - **Triển khai và khôi phục:** cấu hình ngoài repository, không rollback bằng Git; khi có collaborator thứ hai phải tăng approval từ `0` lên `1`. Khi khẩn cấp chỉ administrator được thay đổi/tạm tắt rule và phải ghi lại lý do.

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
- [x] M1.7 Kiểm tra ownership trước khi ghi dữ liệu; không dùng `@PostAuthorize` cho update.
  - **Mục tiêu và phạm vi:** bảo đảm mọi mutation Identity hiện có xác định actor từ access token và kiểm tra owner/quyền quản trị trước khi thay đổi entity; không thêm endpoint nhận target user ID cho API hồ sơ và không mở rộng sang resource của Catalog/Order chưa tồn tại.
  - **Đầu vào và phụ thuộc:** M1.5–M1.6A, subject trong JWT, `Principal`, role/permission hiện hành, `UserProfileService` và các service quản trị hệ thống.
  - **Yêu cầu chức năng:** `/api/auth/my-info` chỉ resolve tài khoản từ principal; ownership policy chạy trước cập nhật hồ sơ/đổi mật khẩu; mutation quản trị tiếp tục kiểm tra hierarchy trước thay đổi trạng thái hoặc role.
  - **Bảo mật và phi chức năng:** thiếu capability trả `403`; mismatch ownership dùng `404` chung để không rò tài nguyên; cấm `@PostAuthorize` trong Identity; request bị từ chối không được flush entity hoặc thu hồi nhầm session.
  - **Đầu ra:** `UserOwnershipPolicy`, tích hợp vào `UserProfileService`, architecture rule và regression test owner/cross-account/no-mutation; giữ mapper Entity → Response hiện có và không tạo migration.
  - **Ví dụ kiểm chứng:** owner cập nhật đúng hồ sơ từ JWT; profile tài khoản khác giữ nguyên; STAFF sửa STAFF khác nhận `403`, status/role/session của target không đổi; mismatch policy nhận `404`.
  - **Nghiệm thu:** targeted tests và `mvnw.cmd spotless:check clean verify` đạt; MySQL Testcontainers, Spring Modulith, ArchUnit, JaCoCo cùng GitHub required checks thành công; review diff không trả entity trực tiếp và không có `@PostAuthorize`.
  - **Triển khai và khôi phục:** không đổi schema/API nên rollout tương thích ngược; rollback bằng revert commit M1.7, không cần database rollback. Policy phải được tái sử dụng khi tương lai xuất hiện endpoint có resource owner tường minh.
- [x] M1.8 Unit, repository, controller và security integration test.
  - Yêu cầu: lập ma trận test cho register, token, introspect, refresh, logout, CAPTCHA, rate limit, my-info, profile, password và admin RBAC; bao phủ happy path, validation, unauthorized, forbidden, replay và concurrency quan trọng.
  - Đầu ra: test độc lập, dữ liệu fixture/builder dễ đọc và báo cáo JaCoCo dùng để phát hiện vùng chưa kiểm chứng; không viết test chỉ để tăng phần trăm coverage.
  - Ví dụ và nghiệm thu: chạy `mvnw.cmd clean verify` với MySQL test thành công, không test flaky, Spring Modulith/ArchUnit đạt và mọi lỗi bảo mật đã biết trong M1 có regression test.
  - Bằng chứng ngày 28/09/2026: 91 test, 0 failure/error/skipped; MySQL 8.0.46 và Flyway V1–V8 đạt; JaCoCo 92,17% line và 73,91% branch; `docker compose config --quiet` và `git diff --check` hợp lệ. PR số 8 vượt `verify`/`dependency-review`; workflow `verify` của merge commit `1ee1474` tiếp tục thành công.

Tiêu chí hoàn thành M1:

- USER không thể tự cấp STAFF/ADMIN hoặc sửa dữ liệu người khác.
- API quản trị áp dụng permission tường minh; chỉ `ADMIN` quản lý role, còn `STAFF` không thể cấp quyền cao hơn quyền đang có.
- Access token và refresh token tách biệt, có rotation/revoke test.
- Trước production, JWT key local/dev phải được thay bằng environment/secret riêng.

### M2 — Catalog và hình ảnh

- [x] M2.1 Category, Product, SKU/variant và migration.
  - Yêu cầu: định nghĩa aggregate ownership, quan hệ category-product-variant, trạng thái publish, SKU duy nhất, giá `BigDecimal` + currency và audit fields; chốt quy tắc xóa mềm trước khi tạo schema.
  - Đầu ra: entity, enum, repository, Flyway migration và mapping tên bảng/cột tường minh trong package Catalog.
  - Ví dụ và nghiệm thu: không tạo được SKU trùng, giá âm hoặc product không có category hợp lệ; migration chạy trên schema rỗng/schema hiện có và repository test đạt.
- [x] M2.2 CRUD sản phẩm với DTO, validation và phân trang.
  - Yêu cầu: API tạo/xem/sửa/ẩn sản phẩm và variant dùng request/response DTO, validation theo trạng thái, optimistic locking và sort/page có allowlist; không binding trực tiếp entity.
  - Đầu ra: controller, mapper, service, error code, OpenAPI và query phân trang ổn định.
  - Ví dụ và nghiệm thu: tạo product hợp lệ trả response chuẩn; input sai trả đúng field error; version cũ trả conflict; page/sort bất hợp lệ không gây query tùy ý.
- [x] M2.3 Upload nhiều ảnh qua abstraction `ObjectStorage`.
  - Yêu cầu: upload nhiều ảnh theo giới hạn số lượng/kích thước/type, kiểm tra magic bytes, sinh object key không đoán được và hỗ trợ ảnh đại diện/thứ tự; business service chỉ gọi abstraction.
  - Đầu ra: `ObjectStorage` port, upload service, metadata entity/DTO, cleanup khi transaction thất bại và fake storage cho test.
  - Ví dụ và nghiệm thu: JPEG/PNG hợp lệ được lưu; file giả MIME, quá lớn hoặc vượt số lượng bị từ chối; lỗi giữa chừng không để metadata/object mồ côi.
  - Bằng chứng ngày 29/09/2026: 127 test, 0 failure/error/skipped; MySQL 8.0.46 áp dụng Flyway V1–V10 và đọc đúng metadata ảnh; JaCoCo 89,59% line/72,10% branch; Spotless, Modulith, ArchUnit, OpenAPI, Docker Compose và Sonar đạt, không phát sinh cảnh báo Sonar mới so với baseline.
- [x] M2.4 MinIO cho local; lưu metadata/URL thay vì Base64.
  - Yêu cầu: cấu hình bucket/policy/endpoint theo profile, health check, presigned URL có TTL khi cần; không lưu binary/Base64 trong MySQL hay response JSON thông thường.
  - Đầu ra: MinIO adapter, Docker Compose local, typed properties có validation và hướng dẫn khởi tạo bucket.
  - Ví dụ và nghiệm thu: upload/download/delete chạy với local MinIO; restart không mất dữ liệu volume; thiếu credential production làm ứng dụng fail-fast và secret không nằm trong Git.
  - Bằng chứng ngày 30/09/2026: 26 test đúng phạm vi M2.4 đạt, gồm MinIO thật qua Testcontainers, presigned URL TTL, bucket private, upload/download/delete, fail-fast credential, image flow, filesystem fallback, Modulith/ArchUnit và Spotless. Compose Silo/MinIO-compatible healthy; restart container giữ object trong named volume; ứng dụng dev health `UP`, Swagger `200`; 500/500 ảnh demo đã được đối soát trong bucket. Sonar và full regression được hoãn đúng quy ước đến khi đóng M2.
- [x] M2.5 Tìm kiếm bằng query/index trước; chỉ dùng stored procedure sau benchmark.
  - Yêu cầu: xác định trường tìm kiếm/filter/sort, chuẩn hóa keyword và index dựa trên query plan; dùng query repository rõ ràng trước khi cân nhắc stored procedure.
  - Đầu ra: search request/response, query/index migration, benchmark dataset/kịch bản và tài liệu quyết định kỹ thuật.
  - Ví dụ và nghiệm thu: tìm theo tên/SKU/category và filter trạng thái cho kết quả ổn định; query không full-scan ngoài ngưỡng đã chốt; ký tự đặc biệt không phá truy vấn.
  - Bằng chứng ngày 30/09/2026: 27 lượt test đúng phạm vi qua H2/MySQL 8.0.46 và kiểm tra kiến trúc đều đạt; Spotless, `git diff --check`, Docker Compose và Flyway V11 đạt. Benchmark 50.000 product/100.000 variant cho warm run page + count khoảng 50,3 ms; FULLTEXT, SKU prefix và category/status đều dùng đúng index, không full scan bảng gốc. Database local nâng V10 lên V11 mà vẫn giữ 20 category, 500 product, 1.000 variant và 500 image. PR số 16 vượt `verify`/`dependency-review`, merge commit `f66da7b` có workflow `verify` thành công.
- [x] M2.6 Admin authorization, test file type/size và integration test.
  - **Mục tiêu và phạm vi:** tách API Catalog công khai chỉ đọc khỏi API quản trị, thay `ROLE_ADMIN` tạm bằng permission chi tiết và đóng quality gate M2; chưa thêm ownership shop/seller vì domain hiện chưa có chủ sở hữu sản phẩm.
  - **Đầu vào và phụ thuộc:** M2.1–M2.5, JWT chứa permission của role, validator/MinIO adapter M2.3–M2.4, search query M2.5 và dữ liệu demo local hiện có.
  - **Yêu cầu chức năng:** khách chỉ xem danh mục hoạt động, product `PUBLISHED`, biến thể đang bán và ảnh qua URL đọc có thời hạn; endpoint quản trị đọc/ghi/publish/ảnh dùng bốn permission độc lập; query `status` từ client không thể làm lộ draft trên API công khai.
  - **Bảo mật và phi chức năng:** `/api/catalog/**` chỉ permit anonymous cho HTTP `GET`; `/api/admin/catalog/**` yêu cầu JWT và `@PreAuthorize`; response công khai không lộ status/version/object key/tên file gốc/audit metadata; migration thu hồi refresh session cũ để JWT mới nhận permission; upload tiếp tục kiểm tra size, MIME, magic bytes, decode ảnh và pixel.
  - **Đầu ra:** `CatalogAuthority`, namespace API công khai/quản trị, storefront controller/service/mapper/request-response DTO, truy vấn detail an toàn, Flyway V12, OpenAPI/architecture document và integration/architecture/migration test.
  - **Ví dụ kiểm chứng:** anonymous đọc product publish thành công nhưng draft trả `404`; USER mutation trả `403` và không tạo dữ liệu; role tùy chỉnh chỉ có `CATALOG_WRITE` không thể đọc/publish; quyền publish và quản lý ảnh hoạt động độc lập; file giả hoặc quá giới hạn tiếp tục bị từ chối.
  - **Nghiệm thu:** `mvnw.cmd spotless:check clean verify` đạt 141 test; line coverage 90,24%, branch coverage 73,29%; Flyway V1–V12 chạy trên H2/MySQL 8.0.46; MinIO Testcontainers, Spring Modulith, ArchUnit, OpenAPI, `git diff --check` và `docker compose config --quiet` đạt; Sonar không còn cảnh báo mới trong phần M2.6.
  - **Triển khai và khôi phục:** V12 chỉ thêm permission/liên kết role và revoke refresh session, không sửa/xóa dữ liệu Catalog; rollout yêu cầu người dùng đăng nhập lại. Flyway đã chạy thì không sửa checksum hoặc rollback file; khi cần khôi phục dùng migration tiến V13 để thu hồi permission/điều chỉnh mapping, còn application có thể revert commit tương ứng.

Tiêu chí hoàn thành M2:

- Giá dùng `BigDecimal` kèm currency.
- SKU là đơn vị bán và là khóa tham chiếu của Inventory.
- File được kiểm tra kích thước/nội dung và tên lưu trữ không trùng.

### M3 — Inventory

- [x] M3.1 Stock item, stock movement và stock reservation.
  - Phạm vi dữ liệu: một `StockItem` duy nhất theo SKU/location; lưu `on_hand`, `reserved`, `version`, còn `available = on_hand - reserved` là giá trị dẫn xuất.
  - Bất biến: mọi số dư không âm, `reserved <= on_hand`, quantity giữ hàng dương và TTL phải ở tương lai; database có `CHECK` tương ứng.
  - Biến động: `StockMovement` chỉ được append, lưu cả delta và số dư sau thao tác, có reason/reference/time để truy vết; không có API sửa/xóa lịch sử.
  - Giữ hàng: tạo schema/entity/repository/status/TTL ở M3.1; chưa công bố command reserve vì cập nhật nguyên tử và chống oversell thuộc M3.2.
  - Ranh giới: Inventory gọi named interface `catalog :: inventory` để kiểm tra SKU; không import entity/repository/internal package và không tạo khóa ngoại sang bảng Catalog.
  - API: admin được tìm kiếm/phân trang/xem tồn, khởi tạo tồn, điều chỉnh theo optimistic version và xem movement; response qua DTO/mapper, không trả entity.
  - Bảo mật: thêm `INVENTORY_READ`/`INVENTORY_WRITE` cho ADMIN và STAFF, revoke refresh session cũ khi migration áp dụng.
  - Nghiệm thu: domain/repository/API/authorization/architecture/OpenAPI/Flyway/H2/MySQL test; Spotless, compile và `git diff --check` phải đạt trước khi đánh dấu hoàn thành.
- [x] M3.2 Reserve tồn kho bằng atomic conditional update/locking.
  - **Mục tiêu và phạm vi:** mở command giữ tồn kho theo `reservationId` cho module Order tương lai, cập nhật `reserved_quantity` nguyên tử và ghi reservation/movement cùng transaction; chưa confirm, release, expiration job hoặc replay idempotent vì thuộc M3.3–M3.4.
  - **Đầu vào và phụ thuộc:** M3.1 đã có `StockItem`, `StockReservation`, `StockMovement`, MySQL/InnoDB và public event; Inventory sở hữu dữ liệu, caller chỉ dùng public named interface và không truy cập package `internal`.
  - **Yêu cầu chức năng:** quantity dương, expiration ở tương lai và không vượt TTL tối đa; atomic conditional update chỉ thành công khi `on_hand - reserved_quantity >= quantity`; reservation ở trạng thái `RESERVED`, movement loại `RESERVATION` và balance event phải phản ánh số dư sau thao tác.
  - **Bảo mật và phi chức năng:** command không nhận giá/quyền từ client; không oversell/lost update; mỗi transaction có timeout, deadlock hoặc lock timeout chỉ retry hữu hạn với backoff cấu hình được, còn thiếu hàng/validation không retry; có metric outcome và log khi retry cạn.
  - **Đầu ra:** public reservation contract, service điều phối retry, transactional executor, repository atomic query, configuration, error code/message, metrics, architecture document và test.
  - **Ví dụ kiểm chứng:** giữ hợp lệ làm tăng reserved đúng một lần; thiếu hàng trả conflict và không tạo reservation/movement; hai request tranh SKU cuối chỉ một thành công; lỗi lock lặp vượt giới hạn dừng đúng số attempt.
  - **Nghiệm thu:** unit/domain/service test, repository/API-contract/architecture test, MySQL 8.0.46 concurrency test, Spotless, compile, `git diff --check` và regression liên quan đều đạt.
  - **Triển khai và khôi phục:** không cần migration vì V13 đã có schema reservation và constraint; cấu hình mới có default tương thích. Rollback code về M3.1 chỉ an toàn khi chưa có consumer gọi command; dữ liệu reservation đã tạo phải được giữ để xử lý tiếp, không xóa thủ công.
- [x] M3.3 Confirm, release và expiration job.
  - **Mục tiêu và phạm vi:** hoàn thiện vòng đời giữ hàng từ `RESERVED` sang đúng một trạng thái cuối `CONFIRMED`, `RELEASED` hoặc `EXPIRED`; chưa triển khai replay cùng command theo payload vì thuộc M3.4.
  - **Đầu vào và phụ thuộc:** tái sử dụng public named interface `inventory :: reservation`, entity/bảng/index V13, movement recorder và cơ chế retry/timeout M3.2; không thêm bảng hoặc sửa migration đã áp dụng.
  - **Yêu cầu chức năng:** confirm đồng thời giảm `on_hand` và `reserved`; release/expire chỉ giảm `reserved`; mọi transition ghi movement, balance event và reservation status event trong cùng transaction; confirm sau TTL phải bị từ chối và reservation được expire an toàn.
  - **Concurrency và idempotency trạng thái:** khóa bi quan reservation trước rồi stock item theo thứ tự cố định; trạng thái cuối không được transition lần hai. Job nhiều instance có thể cùng nhìn thấy candidate nhưng kiểm tra lại dưới DB row lock, nên chỉ một transaction trả kho.
  - **Job và vận hành:** quét UTC theo batch giới hạn, mỗi reservation dùng transaction `REQUIRES_NEW`; crash giữa batch không rollback item đã xử lý, lần chạy sau tiếp tục phần còn lại; có enable/fixed-delay/initial-delay/batch-size, metrics outcome và log summary không chứa dữ liệu nhạy cảm.
  - **Đầu ra:** command confirm/release, lifecycle transaction service, expiration processor/job, typed properties, error code/message, lifecycle event, tài liệu quyết định và test.
  - **Ví dụ kiểm chứng:** confirm giữ hàng hợp lệ tạo `CONFIRMATION`; release tạo `RELEASE`; reservation quá hạn tạo `EXPIRATION`; confirm/release/expire tranh nhau chỉ một trạng thái thắng và số dư/movement chỉ đổi một lần.
  - **Nghiệm thu:** domain/unit/integration/architecture test trên H2, concurrency và multi-instance-style test trên MySQL 8.0.46, Spotless, compile, `git diff --check`, Docker Compose và regression liên quan đều đạt.
  - **Triển khai và khôi phục:** restart ứng dụng để scheduler nhận cấu hình; không có Flyway mới. Có thể tắt job bằng biến môi trường khi rollback code, nhưng không đảo ngược reservation đã sang trạng thái cuối hoặc xóa movement lịch sử.
  - **Bằng chứng ngày 01/10/2026:** 190 test, 0 failure/error/skipped; ba race test MySQL 8.0.46 xác nhận tranh đơn vị tồn cuối, confirm/release và hai batch expiration đều chỉ thay đổi số dư một lần; Flyway V1–V13, H2, MinIO Testcontainers, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check` và Docker Compose đều đạt. Coverage đạt 91,47% instruction, 90,82% line và 72,05% branch; Sonar tiếp tục được hoãn đúng quy ước đến khi đóng M3.
- [x] M3.4 Idempotency theo `reservationId`.
  - **Mục tiêu và phạm vi:** bảo đảm `reserve`, `confirm` và `release` có thể retry an toàn bằng `reservationId`; cùng thao tác/cùng payload phải replay đúng kết quả đã commit, còn cùng thao tác/khác payload phải trả conflict. Không mở REST API nội bộ cho reservation ở monolith và chưa làm M3.5.
  - **Đầu vào và phụ thuộc:** tái sử dụng public named interface `inventory :: reservation`, transaction/locking/retry M3.2–M3.3, movement lịch sử và Flyway V13; Inventory tiếp tục sở hữu toàn bộ dữ liệu idempotency.
  - **Hợp đồng idempotency:** khóa logic là `(reservationId, operation)`; fingerprint SHA-256 được tạo từ payload chuẩn hóa. Kết quả hoàn chỉnh gồm item, quantity, status, expiration và balance sau thao tác phải được lưu cùng transaction nghiệp vụ để replay chính xác sau trường hợp client mất response.
  - **Luật nghiệp vụ:** duplicate cùng fingerprint không cập nhật stock, reservation, movement hoặc phát event lần hai; duplicate khác fingerprint trả mã lỗi nghiệp vụ `409`; confirm/release đối nghịch trên trạng thái cuối vẫn trả lỗi trạng thái, không bị coi là replay hợp lệ; confirm trễ được replay nhất quán thành lỗi expired.
  - **Dữ liệu và migration:** Flyway V14 tạo bảng tiếng Việt, unique constraint và check constraint; JPA dùng tên bảng tường minh. Migration phải chạy được trên H2/MySQL và nâng cấp từ V13 không làm mất reservation/movement cũ.
  - **Concurrency và phục hồi:** record idempotency và thay đổi tồn kho commit nguyên tử; crash trước commit rollback toàn bộ, crash sau commit có snapshot để retry. Race cùng key phải chỉ có một mutation; transaction thua race đọc record đã commit để replay hoặc báo conflict.
  - **Bảo mật và API contract:** reservation vẫn là Java module contract cho Order tương lai, không tạo endpoint có thể bypass security. Các HTTP API Inventory hiện hữu phải tiếp tục trả wrapper `ApiResponse` chuẩn: success có `code=1000` và `result`, error có `code/message` và không rò rỉ `result`.
  - **Tương thích và rollback:** dữ liệu V13 chưa có idempotency record được phục hồi lười từ reservation/movement có bằng chứng; không suy diễn snapshot từ số dư hiện tại. Rollback code không được xóa bảng V14 hoặc lịch sử đã ghi; consumer cũ vẫn dùng nguyên public interface.
  - **Nghiệm thu:** unit/domain/repository/service/API-contract/architecture/migration test trên H2; retry sau response loss và duplicate không tạo movement lần hai; race cùng/different payload trên MySQL 8; Spotless, compile, `git diff --check`, Docker Compose và regression phạm vi đều đạt.
  - **Bằng chứng ngày 01/10/2026:** 200 test, 0 failure/error/skipped; test MySQL 8.0.46 xác nhận request reserve cùng payload replay đúng một kết quả, khác payload có đúng một conflict và confirm đồng thời chỉ mutation một lần. Flyway V1–V14 nâng cấp không mất dữ liệu; H2, MinIO Testcontainers, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check` và Docker Compose đều đạt. Test HTTP kiểm tra success có `code=1000`/`result`, error có `code`/`message` và không có `result`. Coverage đạt 91,33% instruction, 90,79% line và 71,92% branch; Sonar tiếp tục được hoãn đúng quy ước đến khi đóng M3.
  - **Git:** PR `#21` vượt hai required check `verify` và `dependency-review`, merge thành commit `77413b5`; workflow `verify` của merge commit trên `main` tiếp tục thành công.
- [x] M3.5 Concurrency test chứng minh không oversell.
  - **Mục tiêu và phạm vi:** chứng minh bằng test lặp lại trên MySQL thật rằng nhiều transaction đồng thời không thể bán vượt tồn qua chuỗi reserve, confirm và release. Không thêm endpoint, migration hoặc nghiệp vụ mới; chỉ sửa production code nếu test phát hiện invariant đang bị phá vỡ.
  - **Dữ liệu đầu vào:** mỗi vòng tạo stock item biệt lập với tồn đầu kỳ cố định, nhiều reservation id duy nhất và danh sách quantity xác định có tổng lớn hơn tồn; không dùng dữ liệu ngẫu nhiên làm test khó tái hiện.
  - **Điều phối cạnh tranh:** worker dùng barrier/latch để cùng xuất phát và mỗi lời gọi đi qua transaction thật của `StockReservationOperations`; dùng MySQL 8/InnoDB từ Testcontainers, không dùng H2 để kết luận locking.
  - **Kết quả reserve:** mọi worker phải được thu kết quả; chỉ chấp nhận thành công hoặc `INVENTORY_INSUFFICIENT_STOCK`, exception không dự kiến phải làm test thất bại. Tổng quantity thành công phải bằng `reserved`, số reservation/movement/idempotency record phải khớp và `available` không âm.
  - **Kết quả lifecycle:** các reservation thành công được chia xác định thành confirm, release và giữ nguyên rồi xử lý song song. Tổng confirmed cộng reserved còn lại không vượt tồn ban đầu; `on_hand`, `reserved`, `available`, trạng thái và movement phải khớp chính xác với tổng quantity theo từng nhóm.
  - **Khả năng lặp lại và hiệu năng:** chạy ít nhất năm vòng độc lập trong cùng quality gate, có timeout hữu hạn cho barrier và future; không dùng sleep để giả lập đồng thời, không bỏ qua lỗi worker và không để test phụ thuộc thứ tự scheduling.
  - **Đầu ra:** concurrency test dễ đọc, seed deterministic, helper/result type tường minh và báo cáo invariant trước/sau trong README cục bộ; không tạo dữ liệu demo production.
  - **Nghiệm thu:** targeted MySQL test chạy lặp ổn định; full Maven regression, coverage, Spring Modulith/ArchUnit, Flyway V1–V14, H2/MySQL/MinIO, Spotless, `git diff --check`, Docker Compose, Sonar và dependency/security scan cuối milestone M3 đều đạt trước khi merge.
  - **Bằng chứng ngày 01/10/2026:** 24 worker đồng thời chạy 5 vòng deterministic trên MySQL 8.0.46; tổng nhu cầu mỗi vòng lớn hơn tồn ban đầu nhưng chỉ có kết quả thành công hoặc `INVENTORY_INSUFFICIENT_STOCK`. Sau reserve và các transition confirm/release/giữ nguyên chạy song song, `confirmed + reserved <= tồn ban đầu`, `available >= 0`, số dư, trạng thái, movement và idempotency record đều khớp tuyệt đối.
  - **Quality gate đóng M3:** targeted suite đạt 11 test; full `clean verify` đạt 205 test, 0 failure/error/skipped; coverage 91,33% instruction, 90,79% line và 71,92% branch. Flyway V1–V14, H2, MySQL 8.0.46, MinIO Testcontainers, Spring Modulith/ArchUnit, Spotless, Docker Compose và `git diff --check` đều đạt. Sonar còn 7 baseline finding trong 4 file, không có finding mới thuộc M3.5, không có Security Hotspot/Taint Vulnerability; Dependabot không có alert mở.
  - **Git:** PR `#22` vượt hai required check `verify` và `dependency-review`, merge thành commit `f01f2c8`; workflow `verify` của merge commit trên `main` được kiểm tra riêng trước khi đóng nhiệm vụ.

Tiêu chí hoàn thành M3:

- Không thể reserve nhiều hơn `available_quantity`.
- Request gửi lặp không trừ kho hai lần.
- Reservation hết hạn được release an toàn.

### M4 — Cart, Checkout và Order

- [x] M4.1 Cart và cart item.
  - **Mục tiêu và actor:** người dùng đã đăng nhập có đúng một giỏ hàng; có thể xem, thêm SKU, đặt lại số lượng, xóa một mục và xóa toàn bộ giỏ. Chủ sở hữu luôn lấy từ JWT `Principal`, client không được truyền username/user id hoặc truy cập giỏ của người khác.
  - **Ranh giới module:** `order` sở hữu entity, repository và hai bảng `don_hang_gio_hang`, `don_hang_muc_gio_hang`; không tạo foreign key sang Identity/Catalog. Order chỉ phụ thuộc named interface `catalog :: order` để đổi SKU đầu vào thành `productVariantId` và SKU chuẩn hóa đang bán; không import Catalog internal.
  - **Dữ liệu và giá:** cart item chỉ lưu `productVariantId`, SKU chuẩn hóa và quantity; không lưu hoặc tin giá từ client. Giá hiện hành và snapshot chỉ được đọc lại ở checkout M4.3.
  - **Validation và giới hạn:** SKU bắt buộc, đúng định dạng Catalog; quantity từ 1 đến 99; tối đa 100 SKU khác nhau trong một giỏ. Thêm lại cùng variant phải cộng quantity, vượt giới hạn bị từ chối; SKU không còn bán bị từ chối.
  - **Concurrency:** mọi mutation khóa pessimistic cart của đúng owner trước khi đọc/sửa item; khởi tạo cart lười phải an toàn trước unique `owner_subject`. Concurrent add/update không được lost update, tạo item trùng hoặc vượt giới hạn domain/database.
  - **API và response:** `/api/cart` và `/api/cart/items`; controller mỏng, request/response riêng, Bean Validation/ErrorCode/message tiếng Việt, `ApiResponse<T>` và Swagger Bearer metadata. Add trả `201`, get/update/remove/clear trả `200`; endpoint chưa xác thực trả `401`.
  - **Schema:** Flyway V15, UUID `BINARY(16)`, unique owner, unique `(cart_id, product_variant_id)` và `(cart_id, sku)`, check quantity, timestamp/version và index phục vụ lookup. Migration phải chạy trên schema rỗng và upgrade V14 mà không làm thay đổi dữ liệu M3.
  - **Đầu ra:** constants, entity/domain rule, repository, mapper MapStruct, initialization/service, controller, catalog public contract, migration, message, architecture/domain/repository/API/security/migration/concurrency test.
  - **Nghiệm thu:** thêm cùng SKU gộp quantity; update/remove chỉ tác động item của chính owner; clear không ảnh hưởng giỏ khác; invalid quantity/SKU/limit có error contract đúng; concurrent mutation không mất dữ liệu; Spotless, compile, targeted regression, Spring Modulith/ArchUnit, Flyway fresh/upgrade, MySQL concurrency, `git diff --check` và Docker Compose đều đạt trước khi merge.
  - **Ngoài phạm vi:** chưa reserve Inventory, chưa tính giá/checkout, chưa tạo Order/payment, chưa audit purchase flow và chưa thêm dữ liệu demo production.
  - **Bằng chứng ngày 01/10/2026:** full `clean verify` đạt 222 test, 0 failure/error/skipped; instruction coverage 91,37%, line coverage 90,85%, branch coverage 71,40%. Flyway V15 chạy schema rỗng và upgrade V14; MySQL 8.0.46 xác nhận 24 request đồng thời không tạo item trùng hoặc mất quantity, đồng thời 24 first-access chỉ tạo đúng một cart. H2, MySQL, MinIO Testcontainers, Spring Modulith/ArchUnit, OpenAPI, Spotless, JaCoCo, Docker Compose và `git diff --check` đều đạt.
  - **Ổn định test:** GitHub CI lần đầu phát hiện Cart test để lại refresh token làm hai Identity test phụ thuộc thứ tự. Cart test đã tự dọn dữ liệu user/session do chính nó tạo và Identity assertion được giới hạn theo user của test; full regression local và Linux CI sau sửa đều thành công.
  - **Git:** PR `#23` vượt `verify`/`dependency-review`, merge thành commit `4423c30`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh feature đã được xóa local/remote và `main` đồng bộ với `origin/main`.
- [x] M4.2 Order state machine và quy tắc chuyển trạng thái.
  - **Mục tiêu và phạm vi:** tạo aggregate Order tối thiểu để mọi thay đổi trạng thái đi qua một state machine duy nhất, có optimistic version chống ghi đè và phát domain event có cấu trúc. Chưa tạo checkout/API đổi trạng thái, chưa tính giá/snapshot, chưa reserve Inventory, chưa gọi Payment và chưa làm return/refund.
  - **Aggregate và dữ liệu:** module `order` sở hữu entity/table `don_hang_don_dat_hang` gồm UUID, owner subject chuẩn hóa, status, status changed time, version và timestamps. Không tạo foreign key sang Identity/Catalog/Inventory/Payment; Flyway V16 là migration cộng thêm, nâng cấp V15 phải giữ nguyên cart và dữ liệu module cũ.
  - **Trạng thái:** `PENDING`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`; `DELIVERED` và `CANCELLED` là terminal trong phạm vi hiện tại. Client, repository và mapper không được gán status trực tiếp.
  - **Ma trận chuyển trạng thái:** `PENDING + PAYMENT_CONFIRMED/SYSTEM -> PAID`; `PENDING + CANCELLED_BEFORE_PAYMENT/CUSTOMER|STAFF|SYSTEM -> CANCELLED`; `PENDING + PAYMENT_EXPIRED/SYSTEM -> CANCELLED`; `PAID + FULFILLMENT_STARTED/STAFF|SYSTEM -> PROCESSING`; `PAID|PROCESSING + CANCELLATION_COMPENSATED/SYSTEM -> CANCELLED`; `PROCESSING + SHIPMENT_DISPATCHED/STAFF|SYSTEM -> SHIPPED`; `SHIPPED + DELIVERY_CONFIRMED/CUSTOMER|STAFF|SYSTEM -> DELIVERED`. Mọi cặp khác, kể cả `CANCELLED -> PAID`, phải bị từ chối.
  - **An toàn hủy đơn:** sau khi đã thanh toán, chỉ event `CANCELLATION_COMPENSATED` từ `SYSTEM` được đưa đơn về `CANCELLED`; state machine không cho CUSTOMER/STAFF bỏ qua hoàn tiền/trả tồn kho. Việc thực thi compensation thật thuộc M4.5/M5, M4.2 chỉ bảo vệ cổng trạng thái.
  - **Domain event:** mỗi transition thành công phát một event bất biến gồm event ID, order ID, trạng thái trước/sau, transition event, actor type và UTC occurrence time; không chứa token, email hoặc PII. Transition sai và optimistic conflict không được phát event thành công.
  - **Service và lỗi:** transaction service load aggregate, gọi domain method, flush rồi mới publish event; map not-found, invalid transition, actor forbidden và optimistic conflict sang `ErrorCode`/message tiếng Việt ổn định. Không mở REST endpoint ở M4.2 để tránh API cho phép client tự đổi trạng thái.
  - **Concurrency:** cột `@Version` là nguồn kiểm soát lost update; hai transaction cùng đọc một version thì chỉ một transition được commit, transaction còn lại nhận conflict và phải đọc lại trước khi retry theo nghiệp vụ.
  - **Đầu ra:** status/event/actor contract, state machine, aggregate/repository/service/event publisher, Flyway V16, table constant, error contract, architecture/domain/repository/event/migration/concurrency test và transition table cục bộ này.
  - **Nghiệm thu:** unit test bao phủ toàn bộ transition hợp lệ và đại diện mọi transition/actor sai; repository test xác nhận persistence/version; integration test xác nhận event đúng và rollback không phát event thành công; stale update bị từ chối; Flyway chạy schema rỗng và upgrade V15 bảo toàn cart; Spring Modulith/ArchUnit, Spotless, targeted regression, MySQL compatibility, `git diff --check` và Docker Compose đều đạt trước khi merge.
  - **Triển khai và rollback:** rollout chạy V16 trước code hoặc cùng artifact; rollback code không xóa bảng V16. Vì chưa có API tạo Order nên migration không ảnh hưởng client hiện tại; M4.3 không thay đổi schema, còn M4.4 sẽ bổ sung dữ liệu snapshot bằng migration mới, không sửa V16.
  - **Bằng chứng ngày 01/10/2026:** full `clean verify` đạt 235 test, 0 failure/error/skipped; instruction coverage 91,59%, line coverage 91,07%, branch coverage 71,47%. Flyway V16 chạy schema rỗng và upgrade V15 bảo toàn cart/cart item; MySQL 8.0.46, H2, MinIO/Silo Testcontainers, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check` và Docker Compose đều đạt.
  - **Git:** PR `#24` vượt hai required check `verify` và `dependency-review`, merge thành commit `f017ac7`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh feature đã được xóa local/remote, `main` đồng bộ với `origin/main`; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- [x] M4.3 Checkout tính giá hoàn toàn ở server.
  - **Mục tiêu và actor:** người dùng đã đăng nhập yêu cầu báo giá cho đúng giỏ hàng của mình; owner lấy từ JWT `Principal`, client chỉ gửi `expectedCartVersion` để phát hiện giỏ đã thay đổi.
  - **Nguồn dữ liệu:** Order đọc item/quantity từ bảng giỏ hàng và gọi named interface `catalog :: order` theo lô để lấy tên, SKU, giá, currency và trạng thái bán hiện tại; không import Catalog internal, không tạo FK xuyên module và không tin giá/tổng/items client gửi lên.
  - **Availability:** M4.3 kiểm tra category/product/variant còn được phép bán. Khả dụng tồn kho chỉ có ý nghĩa chắc chắn khi reserve nguyên tử nên thuộc M4.5, không trả cam kết tồn kho giả trong quote.
  - **Chính sách giá:** dùng money value object dựa trên ISO-4217, rounding `HALF_UP` theo fraction digits của currency; một quote chỉ chấp nhận một currency. Discount/tax lấy từ cấu hình server `app.order.checkout.pricing`, áp dụng và làm tròn theo từng line; mặc định bằng 0 cho đến khi có nguồn promotion/tax riêng.
  - **API và response:** `POST /api/checkout/quote` yêu cầu Bearer token; response có cart/version, thời điểm tính, currency, từng line cùng subtotal/discount/tax/total và tổng hợp toàn quote. Không trả entity JPA.
  - **Lỗi và bảo mật:** empty cart, stale cart version, item không còn bán và mixed currency có `ErrorCode`/message tiếng Việt ổn định; request thiếu/âm version bị validation; dữ liệu giá giả mạo trong JSON không tham gia phép tính.
  - **Ngoài phạm vi:** chưa tạo Order, chưa lưu quote/snapshot, chưa reserve Inventory, chưa áp coupon cá nhân, chưa payment và chưa cung cấp idempotency cho hành vi tạo đơn.
  - **Đầu ra:** Catalog pricing contract/service theo lô, checkout vertical slice, money/pricing calculator, properties, request/response/controller, error/message, architecture/domain/service/API/OpenAPI test. Không cần migration ở M4.3.
  - **Nghiệm thu:** giá Catalog thay đổi sau khi add cart phải được dùng ở quote; client sửa giá/tổng không ảnh hưởng; toàn bộ line và tổng reconcile; rounding/currency/stale/empty/unavailable/authentication/ownership được kiểm thử; Spring Modulith/ArchUnit, Spotless, targeted regression, MySQL compatibility, `git diff --check` và Docker Compose đạt trước khi merge.
  - **Triển khai và rollback:** cấu hình mới có default an toàn `0`, nên triển khai tương thích ngược; rollback code không cần rollback schema. Không dùng quote M4.3 như cam kết giá lâu dài—M4.4 phải tính lại và lưu snapshot khi tạo đơn.
  - **Bằng chứng ngày 01/10/2026:** full `clean verify` đạt 244 test, 0 failure/error/skipped; instruction coverage 91,56%, line coverage 91,06%, branch coverage 70,86%. MySQL 8.0.46, H2, Testcontainers, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check` và Docker Compose đều đạt; Flyway giữ nguyên tại V16 vì nhiệm vụ không thay đổi schema.
  - **Git:** PR `#25` vượt hai required check `verify` và `dependency-review`, merge thành commit `6313277`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh feature đã được xóa local/remote, `main` đồng bộ với `origin/main`; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- [x] M4.4 Order item lưu snapshot tên, SKU, giá, giảm giá, thuế và currency.
  - **Mục tiêu và actor:** tạo Order `PENDING` nội bộ từ kết quả định giá giỏ hàng của đúng người dùng; task này chưa mở endpoint tạo đơn để tránh tạo trùng trước M4.6.
  - **Dữ liệu sở hữu:** Order lưu item snapshot gồm variant id tham chiếu logic, thứ tự dòng, SKU, tên, quantity, unit price, subtotal, discount, tax, total, currency và thời điểm tạo. Không tạo foreign key sang Catalog; chỉ có foreign key nội module từ item tới order.
  - **Bất biến:** order mới phải có ít nhất một item, mọi item cùng currency, số tiền không âm, discount không vượt subtotal và `total = subtotal - discount + tax`; collection chỉ đọc và các cột snapshot không được update sau insert.
  - **Ranh giới:** mapper chuyển `CheckoutPricingBreakdown` thành draft snapshot; entity không phụ thuộc Catalog hay DTO/API. Giá phải được tính lại từ nguồn server ngay trước khi persist.
  - **Schema:** Flyway V17 tạo `don_hang_muc_don_hang` với UUID, decimal cố định, unique thứ tự dòng, check constraint, index và FK cascade nội module; upgrade V16 phải giữ nguyên order/cart hiện có.
  - **Ngoài phạm vi:** chưa reserve/release tồn kho, chưa public create-order API, chưa idempotency key, chưa payment và chưa clear cart.
  - **Nghiệm thu:** persist/reload giữ đúng toàn bộ snapshot; đổi tên/giá/ẩn Catalog sau đó không đổi order cũ; snapshot không có mutation API; fresh/upgrade migration, H2/MySQL, architecture, Spotless, `git diff --check` và Docker Compose đều đạt trước khi merge.
  - **Bằng chứng ngày 02/10/2026:** full `clean verify` đạt 251 test, 0 failure/error/skipped; instruction coverage 91,66%, line coverage 91,22%, branch coverage 70,78%. Flyway V1–V17 fresh/upgrade trên H2 và MySQL 8.0.46, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check` và Docker Compose đều đạt. Test chứng minh đổi tên, giá hoặc ẩn sản phẩm sau khi tạo đơn không làm thay đổi snapshot cũ.
  - **Git:** PR `#26` vượt hai required check `verify` và `dependency-review`, merge thành commit `76514a0`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh feature đã được xóa local/remote, `main` đồng bộ với `origin/main`; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- [x] M4.5 Điều phối reserve/release inventory bằng domain event.
  - **Mục tiêu:** sau khi Order và snapshot được commit, phát yêu cầu giữ tồn kho; Order chỉ gọi named interface `inventory :: reservation`, tuyệt đối không truy cập entity/repository nội bộ của Inventory.
  - **Định danh và chống lặp:** mỗi luồng có `eventId`, `correlationId`, `orderId`; mỗi dòng có reservation ID ổn định. Event trùng phải no-op hoặc replay cùng kết quả, không được giữ/trả kho lần hai.
  - **Trạng thái bền vững:** lưu orchestration và từng dòng reservation với optimistic version, thời điểm, mã lỗi và trạng thái rõ ràng; event được xử lý sau commit để listener không nhìn thấy Order chưa commit và cấu trúc phải sẵn sàng cho outbox M6.1.
  - **Luồng thành công:** resolve stock target qua port công khai của Inventory, reserve từng dòng với TTL cấu hình; khi tất cả thành công, orchestration chuyển `RESERVED` để M5 có thể thực hiện payment. Order vẫn `PENDING` cho tới khi payment được xác nhận qua state machine.
  - **Luồng lỗi:** lỗi nghiệp vụ không retry được phải release mọi dòng đã reserve và kết thúc `FAILED`; timeout/tạm thời chuyển `RETRY_REQUIRED`; release bù thất bại chuyển `COMPENSATION_REQUIRED` và phát compensation event để không che giấu lệch tồn kho.
  - **Schema/cấu hình:** Flyway V18 tạo bảng điều phối và chi tiết bằng tên tiếng Việt, FK chỉ trong module Order, không FK chéo Inventory; cấu hình location mặc định và TTL có validation/default an toàn.
  - **Ngoài phạm vi:** chưa mở checkout REST/idempotency key (M4.6), chưa tự động reconciliation/failure injection (M4.7), chưa confirm reservation hay payment (M5), chưa persistent outbox (M6.1).
  - **Nghiệm thu:** success, thiếu hàng, stock target không tồn tại, timeout, partial reserve + compensation, compensation thất bại và duplicate event đều có test; fresh/upgrade H2/MySQL, module boundary, Spotless, JaCoCo, `git diff --check` và Docker Compose phải đạt trước khi merge.
  - **Bằng chứng ngày 02/10/2026:** full `clean verify` đạt 264 test, 0 failure/error/skipped; instruction coverage 91,30%, line coverage 91,09%, branch coverage 69,78%. Flyway V1–V18 fresh/upgrade trên H2 và MySQL 8.0.46, luồng success/duplicate/thiếu stock/partial compensation, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check` và Docker Compose đều đạt.
  - **Git:** PR `#27` vượt hai required check `verify` và `dependency-review`, merge thành commit `54a5b4d`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh feature đã được xóa local/remote, `main` đồng bộ với `origin/main`; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- [x] M4.6 Idempotency key cho checkout.
  - **Mục tiêu và phạm vi:** mở `POST /api/checkout/orders` cho người dùng đã đăng nhập và bảo đảm việc client retry do timeout/mất kết nối không tạo thêm Order hoặc giữ tồn kho lần hai. Chỉ bảo vệ thao tác tạo đơn; reconciliation tổng quát và fault injection thuộc M4.7, payment thuộc M5, outbox thuộc M6.1.
  - **Đầu vào và phụ thuộc:** dùng giỏ hàng/version của M4.1, định giá server-side của M4.3, snapshot Order của M4.4 và `correlationId` điều phối inventory của M4.5. Order sở hữu bản ghi idempotency; không truy cập repository hay bảng của module khác.
  - **Yêu cầu chức năng:** header `Idempotency-Key` gồm 8–128 ký tự ASCII hiển thị; scope là `(ownerSubject, CREATE_ORDER, keyHash)`; fingerprint bao gồm operation và `expectedCartVersion`. Bản ghi có `PROCESSING`, `COMPLETED`, `FAILED`, TTL mặc định 24 giờ và replay đúng Order/lỗi đã commit. Cùng key nhưng payload khác trả `409`; cùng key của user khác là scope độc lập.
  - **Bảo mật và phi chức năng:** endpoint bắt buộc Bearer JWT và luôn lấy owner từ `Principal`; chỉ lưu SHA-256 của key, không lưu raw key. Unique constraint, pessimistic lock, optimistic version và transaction `REQUIRES_NEW` bảo vệ cạnh tranh. Bản ghi `PROCESSING` không tự restart hoặc bị cleanup khi hết TTL vì request cũ có thể vẫn đang chạy; timeout sau khi Order commit được khôi phục qua `correlationId` thay vì tạo Order mới.
  - **Đầu ra:** request/controller/OpenAPI, idempotency service/state service/entity/repository/cleanup job/config, error/message tiếng Việt và Flyway V19 với bảng `don_hang_yeu_cau_luy_dang`, unique/check/index/FK nội module. Response tiếp tục dùng `ApiResponse<OrderSnapshotResponse>` và không trả entity.
  - **Ví dụ kiểm chứng:** retry cùng key/payload trả cùng Order và chỉ có một movement giữ hàng; khác payload trả `1322/409`; thiếu/sai key trả `1320/1321`; user khác dùng cùng key tạo scope riêng; lỗi nghiệp vụ được replay; khoảng lỗi sau Order commit nhưng trước khi lưu kết quả vẫn khôi phục cùng Order; hai request MySQL đồng thời không tạo trùng dù InnoDB chọn một transaction làm deadlock victim.
  - **Nghiệm thu:** test entity/properties/cleanup/recovery/API/OpenAPI/architecture/fresh-upgrade migration và concurrency MySQL 8.0.46; full `clean verify`, Spotless, Spring Modulith/ArchUnit, JaCoCo, `git diff --check`, code-smell scan và Docker Compose đều phải đạt trước khi merge.
  - **Triển khai và khôi phục:** chạy backup rồi áp V19 trước hoặc cùng artifact; V19 chỉ thêm bảng nên tương thích ngược. Rollback code giữ bảng V19 vô hại, không sửa/xóa migration đã áp dụng. Cleanup chỉ xóa bản ghi terminal hết hạn; bản ghi `PROCESSING` được giữ cho M4.7 reconciliation.
  - **Bằng chứng ngày 02/10/2026:** full `clean verify` đạt 277 test, 0 failure/error/skipped; instruction coverage 91,16%, line coverage 90,96%, branch coverage 69,85%. Flyway V1–V19 fresh/upgrade trên H2 và MySQL 8.0.46, test tranh chấp/deadlock/recovery/cleanup, Spring Modulith/ArchUnit, Spotless, JaCoCo, `git diff --check`, code-smell scan và Docker Compose đều đạt.
  - **Git:** PR `#28` vượt hai required check `verify` và `dependency-review`, merge thành commit `e5b64ea`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh feature đã được xóa local/remote, `main` đồng bộ với `origin/main`; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- [x] M4.7 Test lỗi giữa chừng và compensation.
  - **Mục tiêu và phạm vi:** tự phục hồi luồng Order–Inventory khi event sau commit bị mất, tiến trình dừng giữa reserve và cập nhật trạng thái Order, hoặc compensation dừng giữa chừng. Failure point trước/sau payment chỉ được kiểm thử khi M5 có payment aggregate/provider; M4.7 không tạo payment giả hoặc cho phép Order chuyển `PAID` ngoài state machine.
  - **Mô hình phục hồi:** job theo lịch chỉ chọn orchestration `REQUESTED`, `PROCESSING`, `RETRY_REQUIRED`, `COMPENSATING` hoặc `COMPENSATION_REQUIRED` đã quá ngưỡng stale; mỗi bản ghi được khóa bi quan và kiểm tra lại trước khi claim. Batch có giới hạn, cô lập lỗi từng item và không để một order lỗi chặn các order khác.
  - **Retry an toàn:** reserve dùng lại reservation ID, expiration và payload ban đầu nên Inventory replay kết quả đã commit thay vì trừ kho lần hai. `PROCESSING` bị gián đoạn được đưa qua trạng thái retry hợp lệ; bản ghi còn mới không bị scheduler chiếm mất.
  - **Compensation an toàn:** các dòng `RESERVED` được release idempotent; reservation đã `EXPIRED` được coi là đã giải phóng thay vì retry vô hạn. Khi tất cả dòng đã giải phóng, orchestration thành `FAILED` và Order được hủy qua state machine; phần còn lỗi quay về `COMPENSATION_REQUIRED` để lần chạy sau xử lý tiếp.
  - **Cấu hình và vận hành:** `app.order.checkout.inventory.reconciliation` có cờ bật, stale threshold, initial/fixed delay và batch size với default/validation an toàn. Metric run/item chỉ dùng tag hữu hạn; log chứa định danh kỹ thuật và loại lỗi, không chứa token/PII.
  - **Failure injection:** test chỉ tạo khoảng lỗi bằng fixture/mutation dữ liệu trong môi trường test; production không có backdoor hoặc endpoint bật lỗi. Integration test phải chứng minh event bị mất được xử lý, reserve đã commit được replay đúng một lần, compensation được tiếp tục, và item `PROCESSING` còn mới không bị lấy.
  - **Runbook order mắc kẹt:** cảnh báo khi metric `shop.order.checkout.inventory.reconciliation.items{outcome="failed"}` tăng hoặc còn bản ghi non-terminal quá stale; tra cứu theo `request_event_id`/`correlation_id`, đối chiếu reservation/movement bên Inventory, sửa nguyên nhân hạ tầng rồi để reconciliation retry. Không cập nhật trực tiếp Order thành `PAID`, không xóa movement/idempotency; nếu compensation vẫn lỗi phải giữ `COMPENSATION_REQUIRED` và chuyển xử lý thủ công có audit.
  - **Nghiệm thu đóng M4:** không tạo hai reservation/movement khi replay, không còn giữ tồn kho sau compensation thành công, lỗi từng item có metric/cảnh báo, full regression/fresh-upgrade Flyway/H2/MySQL/Spring Modulith/ArchUnit/Spotless/JaCoCo/Docker Compose/Sonar và dependency-security scan đều đạt trước khi merge.
  - **Bằng chứng ngày 02–03/10/2026:** `mvnw.cmd clean verify` đạt 287 test, 0 failure/error/skipped; Flyway V1–V19 chạy fresh/upgrade trên H2 và MySQL 8.0.46; Testcontainers kiểm tra MySQL, MinIO, replay reservation và reconciliation thực tế. Coverage đạt 91,32% instruction, 91,11% line và 69,67% branch; Spring Modulith/ArchUnit, Spotless, `git diff --check` và `docker compose config --quiet` đều đạt. Sonar không có issue, Security Hotspot hoặc Taint Vulnerability trong phần thay đổi M4.7; báo cáo toàn dự án còn 65 code smell cũ/ngoài phạm vi M4.7 cần được xử lý theo backlog kỹ thuật. PR `#29` đã vượt cả `verify` và `dependency-review` trên commit triển khai `7d8e48e` trước bước cập nhật trạng thái tài liệu cuối.

Tiêu chí hoàn thành M4:

- Thay đổi giá sản phẩm không làm thay đổi đơn cũ.
- Không sinh hai đơn khi client retry cùng idempotency key.
- Mọi trạng thái order đều đi qua state machine hợp lệ.

### M5 — Payment

- [x] M5.1 Payment aggregate và provider interface.
  - **Mục tiêu và actor:** dựng lõi Payment độc lập để hệ thống có thể lưu từng lần thử thanh toán của một Order và giao tiếp với provider qua port trung lập. Actor kỹ thuật là Order orchestration và provider adapter; M5.1 chưa mở REST API cho client và chưa thực hiện charge thật.
  - **Sở hữu dữ liệu và ranh giới module:** Payment sở hữu aggregate, repository và bảng `thanh_toan_*`; `order_id` chỉ là định danh tham chiếu, không tạo database foreign key xuyên module. Order về sau chỉ được dùng named interface/event công khai của Payment, tuyệt đối không import Payment entity/repository hoặc SDK provider.
  - **Bất biến aggregate:** mỗi attempt có UUID, `orderId`, `attemptNumber`, amount dương với scale tiền tệ hữu hạn, ISO currency ba chữ cái, provider code, provider reference tùy trạng thái, status, failure code và audit time/version. Snapshot amount/currency bất biến sau khi tạo; cặp `(orderId, attemptNumber)` và `(providerCode, providerReference)` phải duy nhất khi reference tồn tại.
  - **State machine:** trạng thái gồm `CREATED`, `PENDING`, `REQUIRES_ACTION`, `UNKNOWN`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`; terminal không được chuyển tiếp. Chỉ cho phép các cạnh tiến hợp lệ; thời điểm mới không được trước lần thay đổi trước; `FAILED` bắt buộc có failure code, còn status khác không được giữ failure code.
  - **Provider contract:** named interface công khai nhận request chứa payment/order ID, amount/currency và idempotency key do server cấp; trả status chuẩn hóa, provider reference, redirect URI khi cần action và failure code khi bị từ chối. Port không lộ SDK/type cụ thể và không nhận PAN/CVV.
  - **Error taxonomy:** exception của outbound provider chỉ chứa mã lỗi đã chuẩn hóa và loại `TIMEOUT`, `TEMPORARY_UNAVAILABLE`, `RATE_LIMITED`, `INVALID_REQUEST`, `AUTHENTICATION_FAILED`, `CONFIGURATION_ERROR`, `PROTOCOL_ERROR`; mỗi loại khai báo rõ retryable hay non-retryable, không chứa raw body/token/credential.
  - **Migration và triển khai:** Flyway V20 chỉ thêm bảng/index/constraint, tương thích ngược với artifact M4 và có thể roll-forward an toàn; rollback code không xóa bảng hoặc sửa migration đã áp dụng.
  - **Ngoài phạm vi:** fake provider/profile (M5.2), hosted checkout/provider thật và resilience (M5.3), webhook/dedup (M5.4), cập nhật Order/Inventory qua event (M5.5), reconciliation (M5.6), refund và API quản trị.
  - **Nghiệm thu:** domain test chứng minh amount/currency/provider data và state transition; repository/schema test chứng minh mapping, unique/check/index và không có FK xuyên module; Flyway fresh/upgrade, Spring Modulith/ArchUnit, Spotless, `git diff --check` và regression có nguy cơ ảnh hưởng đều đạt. Không đánh dấu `[x]` cho đến khi PR vượt `verify` và `dependency-review`.
  - **Bằng chứng:** 307 test, 0 failure, 0 error, 0 skipped; instruction coverage 91,19%, line coverage 91,04%, branch coverage 69,84%. Maven `clean verify`, Spotless, Spring Modulith, ArchUnit, Flyway fresh/upgrade V1–V20, H2, MySQL 8.0.46, Docker Compose và `git diff --check` đều đạt. PR số `30` đã vượt `verify` và `dependency-review` trên implementation commit; commit tài liệu đóng task tiếp tục phải vượt lại hai required checks trước khi merge.
- [x] M5.2 Fake payment provider để hoàn chỉnh luồng trước.
  - **Mục tiêu và actor:** hoàn chỉnh luồng Payment nội bộ từ application port đến provider port để Order orchestration về sau có thể yêu cầu thanh toán mà không phụ thuộc SDK. M5.2 dùng fake adapter cho developer và test; chưa mở REST cho client vì client không được tự quyết định amount, currency hoặc payment status.
  - **Luồng xử lý:** application service tạo hoặc đọc lại payment attempt theo ID/cặp order-attempt, sinh provider idempotency key ổn định từ payment attempt ID, commit trạng thái `CREATED` trước khi gọi provider, sau đó ghi kết quả trong transaction riêng. Crash giữa hai transaction để attempt ở trạng thái có thể retry/reconcile, không giữ database transaction trong lúc gọi outbound.
  - **Kịch bản fake:** hỗ trợ `SUCCESS`, `DECLINE`, `TIMEOUT`, `PENDING`; test control có thể chọn mode theo payment attempt mà không cần restart context. Cùng idempotency key/cùng payload replay đúng kết quả; cùng key/khác payload bị từ chối. Callback simulator tạo delivery lặp với cùng provider event ID để chuẩn bị test dedup ở M5.4 nhưng chưa xử lý webhook trong task này.
  - **Ánh xạ trạng thái:** success → `SUCCEEDED`, decline → `FAILED` với failure code chuẩn hóa, timeout/retryable error → `UNKNOWN`, pending → `PENDING`. Attempt terminal hoặc pending đã có kết quả không gọi provider lại; retry attempt `CREATED/UNKNOWN` dùng lại idempotency key.
  - **Cấu hình và an toàn:** provider mặc định là `NONE`; profile `dev/test` chọn `FAKE`. Fake code không nhận/lưu PAN, CVV, token hay credential; reference/event ID sinh deterministic từ identifier kỹ thuật. Nếu profile `prod` chọn `FAKE`, startup phải fail-fast trước khi nhận traffic.
  - **Ngoài phạm vi:** provider thật, hosted redirect, timeout/circuit breaker mạng thật (M5.3), ký/xác minh webhook và inbox dedup (M5.4), cập nhật Order/Inventory qua event (M5.5), reconciliation (M5.6), REST API payment cho client và refund.
  - **Nghiệm thu:** integration test chứng minh bốn mode đưa payment về đúng trạng thái và dữ liệu được persist; unit/contract test chứng minh replay/mismatch/duplicate callback; configuration test chứng minh fake chỉ tồn tại khi được chọn và production fail-fast. Spring Modulith/ArchUnit, Spotless, regression liên quan, `git diff --check` và Docker Compose phải đạt; chỉ đổi `[x]` sau required checks của PR.
  - **Bằng chứng:** 324 test, 0 failure, 0 error, 0 skipped; instruction coverage 91,24%, line coverage 91,08%, branch coverage 69,97%. Maven `clean verify`, Spotless, Spring Modulith, ArchUnit, Flyway V1–V20, H2, MySQL 8.0.46, MinIO Testcontainers, Docker Compose và `git diff --check` đều đạt. PR số `31` đã vượt `verify` và `dependency-review` trên implementation commit; commit tài liệu đóng task tiếp tục phải vượt lại hai required checks trước khi merge.
- [~] M5.3 Tích hợp provider thật bằng hosted checkout/token; không lưu dữ liệu thẻ.
  - Yêu cầu: dùng hosted page/tokenization, credential từ secret store, TLS, timeout/circuit breaker và redirect allowlist; hoàn thành PCI scope review trước go-live.
  - Đầu ra: provider adapter, typed config, request signing/authentication, return/cancel handler và sanitized operational docs.
  - Ví dụ và nghiệm thu: tạo session và return hợp lệ hoạt động trên sandbox; URL giả, amount mismatch, timeout và provider error được xử lý; database/log không có PAN/CVV.
  - **Thiết kế đang triển khai:** Stripe Checkout Session ở `ui_mode=hosted_page` theo API version đang pin; Shop chỉ gửi order/payment-attempt ID, amount/currency do server xác lập và nhận session ID cùng HTTPS checkout URL. Request `POST` dùng `Idempotency-Key` ổn định theo payment attempt nên chỉ retry lỗi tạm thời; không có trường card/PAN/CVV trong provider port, entity hoặc migration.
  - **Biên an toàn:** API Stripe cố định tại `https://api.stripe.com`; checkout URL chỉ được chấp nhận khi dùng HTTPS, cổng mặc định/443 và host nằm trong `STRIPE_ALLOWED_CHECKOUT_HOSTS`. Adapter đối chiếu lại order reference, amount theo minor unit và currency trước khi lưu URL; response quá lớn, sai content type, sai JSON hoặc sai dữ liệu đều bị coi là protocol error.
  - **Return/cancel:** success/cancel URL phải trỏ đúng `/api/payments/checkout/return` và `/api/payments/checkout/cancel`, đồng thời host phải thuộc `STRIPE_ALLOWED_RETURN_HOSTS`. HTTPS là bắt buộc, ngoại trừ `http://localhost` hoặc `http://127.0.0.1` chỉ được phép với khóa `sk_test_` để smoke test local; khóa `sk_live_` không có ngoại lệ này. Mỗi URL mang state HMAC-SHA256 có hạn dùng và gắn với payment attempt; return còn phải khớp Stripe session ID đã lưu. Trình duyệt quay lại hoặc bấm hủy không được tự đánh dấu thanh toán thành công/thất bại; M5.4 mới tin webhook đã xác minh chữ ký.
  - **Resilience/telemetry:** OkHttp tắt retry ngầm và áp dụng connect/read/call timeout riêng. Resilience4j retry tối đa 3 lần chỉ cho lỗi retryable, circuit breaker đếm đúng lỗi tạm thời và xuất Micrometer metrics cho circuit breaker/retry; không có fallback giả success.
  - **Biến môi trường bắt buộc khi chọn `PAYMENT_PROVIDER_TYPE=stripe`:** `STRIPE_SECRET_KEY`, `STRIPE_RETURN_STATE_SECRET`, `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`, `STRIPE_ALLOWED_RETURN_HOSTS`. Không commit giá trị secret; production lấy từ secret manager/runtime injection. API mặc định pin `2026-09-30.endive`; các timeout, retry, circuit breaker, API version và checkout-host allowlist có biến override tương ứng trong `application.yml`. Khi tạo webhook ở M5.4 phải dùng cùng API version.
  - **Runbook sandbox:** dùng Stripe test secret, backend có callback HTTPS công khai và host đã allowlist; tạo payment attempt từ order sandbox, mở action URL, dùng test card trên trang Stripe rồi kiểm tra return chỉ báo `REQUIRES_ACTION` cho đến khi webhook M5.4 xác nhận. Kiểm tra metrics `resilience4j.circuitbreaker.*`/`resilience4j.retry.*`, bảng `thanh_toan_lan_thu` chỉ có `provider_reference` và `action_url`, đồng thời xoay ngay credential nếu từng xuất hiện trong log hoặc Git.
  - **Smoke test sandbox có chủ đích:** `StripeSandboxSmokeIT` không khớp naming convention test mặc định nên không gọi Stripe trong `clean verify`/CI. Chỉ chạy bằng `mvnw.cmd -Dtest=StripeSandboxSmokeIT -Dstripe.sandbox.enabled=true test` khi process test đã có `STRIPE_SECRET_KEY=sk_test_...` và `STRIPE_RETURN_STATE_SECRET`; test đi qua `PaymentInitiationOperations`, tạo đúng một Checkout Session thật, replay không gọi provider lần hai và giữ bản ghi `REQUIRES_ACTION` trong MySQL local để đối chiếu. Test tự từ chối `sk_live_` và không in credential/action URL.
  - **Bằng chứng local ngày 03/10/2026:** 359 test mặc định, 0 failure/error/skipped; instruction coverage 91,30%, line coverage 91,19%, branch coverage 69,20%. Maven `clean verify`, Spring Modulith/ArchUnit, Spotless, Flyway fresh/upgrade V1–V21, H2, MySQL 8.0.46, MinIO Testcontainers, Docker Compose, URL/amount/currency/reference validation, timeout/retry/circuit breaker, return/cancel security, quét credential và `git diff --check` đều đạt. Test bổ sung xác nhận HTTP loopback chỉ được dùng với `sk_test_`; khóa live, host ngoài loopback và callback sai path đều fail-fast. Test HTTP adapter không chứa credential thật.
  - **Bằng chứng sandbox thật:** smoke test opt-in đạt 1/1 bằng `sk_test_`, tạo Checkout Session `cs_test_*` tại host `checkout.stripe.com`, persist đúng một attempt `REQUIRES_ACTION` trị giá 199.000 VND vào MySQL và replay trả lại bản ghi cũ mà không gọi provider lần hai. Quá trình test phát hiện và sửa tương thích API `ui_mode=hosted_page` cùng việc Stripe Checkout URL hợp lệ có thể mang fragment; credential, session ID đầy đủ và action URL không bị ghi ra log hoặc Git.
  - **Trạng thái nghiệm thu:** quality gate local đã hoàn tất; giữ `[~]` cho đến khi PR vượt `verify` và `dependency-review`, sau đó mới đổi `[x]`, merge và chuyển M5.4.
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
- [ ] M5.7 Tích hợp thanh toán QR động theo mô hình ví/sàn thương mại điện tử.
  - **Mục tiêu:** ưu tiên adapter VNPay/QR cho thị trường Việt Nam; MoMo, ZaloPay hoặc ShopeePay là adapter mở rộng khi có hợp đồng merchant/API phù hợp. Stripe được giữ như provider thẻ quốc tế tùy chọn và adapter tham chiếu; domain Payment không bị khóa vào một SDK cụ thể.
  - **Luồng:** Shop tạo payment attempt và gửi amount/currency/order reference/idempotency key do server xác lập; provider trả QR payload hoặc hosted QR URL cùng thời điểm hết hạn. Frontend chỉ hiển thị dữ liệu provider trả về và có thể polling để cải thiện trải nghiệm, nhưng không được tự báo thanh toán thành công.
  - **Nguồn sự thật:** chỉ webhook đã xác minh chữ ký hoặc reconciliation từ API provider mới được chuyển attempt sang `SUCCEEDED`/`FAILED`/`EXPIRED`; xử lý lặp bằng provider event ID và payment idempotency key.
  - **An toàn:** không tạo QR chuyển khoản tĩnh, không nhúng merchant secret vào QR/client, không lưu PAN/CVV, kiểm tra allowlist URL/kích thước payload/TTL và che QR URL hoặc token khỏi log nếu provider coi đó là bearer credential.
  - **Nghiệm thu:** sandbox chứng minh QR đúng số tiền và đơn hàng, hết hạn đúng, webhook trùng không tạo side effect, browser giả success không có tác dụng, reconciliation sửa được trạng thái mất webhook và database/log không chứa credential.

Tiêu chí hoàn thành M5:

- Webhook lặp không tạo side effect lặp.
- Payment thành công mới confirm kho; thất bại/timeout phải release kho.
- Có audit trail cho mọi lần thử thanh toán.
- QR động hết hạn, webhook lặp/mất và browser giả trạng thái đều được xử lý an toàn trên sandbox provider đã chọn.

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

- [x] M6.9A Chốt phạm vi và danh mục sự kiện.
  - Yêu cầu: tách access log kỹ thuật khỏi audit event; lập ma trận `action_code` gồm điểm phát sinh, actor, resource, outcome, mức nhạy cảm, thời hạn lưu và module sở hữu.
  - Đầu ra: tài liệu danh mục cho các nhóm authentication, user/permission, product, order, inventory, payment, export và system job; request đọc thông thường không được ghi vào database nếu không truy cập dữ liệu nhạy cảm.
  - Nghiệm thu: mọi sự kiện đều có mã ổn định và lý do cần audit; không còn trường hợp dùng nhãn hiển thị làm định danh sự kiện.
  - Bằng chứng ngày 28/09/2026: catalog v1 có 37 `action_code` thuộc 8 prefix module, không trùng và đúng pattern; chốt access log/audit, actor/outcome, retention, redaction, trusted proxy, UTC và page-number pagination. 91 test cũ tiếp tục đạt trên H2/MySQL; PR số 9 vượt `verify`/`dependency-review`, workflow `verify` của merge commit `7916c2e` thành công.
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
- M0.7 thêm `ci/settings.xml` dùng Maven Central HTTPS, `.mvn/maven.config` tự áp dụng settings, `.gitattributes` chuẩn hóa line ending và Dockerfile build bằng Maven Wrapper thay vì Maven cài riêng trong image.
- Bản clone sạch từ GitHub đã chạy `mvnw.cmd clean verify` thành công với 69 test, Flyway trên MySQL 8.0.46, Spotless, Modulith, ArchUnit và JaCoCo; `docker build --check` không có cảnh báo và image build thành công.
- GitHub Actions của merge commit `e3a9e92` kết thúc `success`; `main` đồng bộ với `origin/main`. README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- M0.8 bật branch protection cho `main`, áp dụng cả administrator, yêu cầu branch cập nhật, bắt buộc PR cùng hai check `verify`/`dependency-review`, resolve conversation và chặn force-push/xóa branch. Approval tạm là `0` vì repository chỉ có một collaborator; phải tăng lên `1` khi có reviewer thứ hai.
- Direct push thử nghiệm bị GitHub từ chối bằng `GH006`. PR probe số `6` ban đầu phát hiện `dependency-review` lỗi do Dependabot alerts/dependency graph bị tắt; đã bật Dependabot alerts, xác nhận SBOM 35 package, rerun thành công cả hai job và PR chuyển sang `clean`.
- PR probe đã đóng mà không merge; remote/local branch cùng tệp probe đã được xóa. `main` không nhận commit thử nghiệm và vẫn đồng bộ với `origin/main` tại `e3a9e92`.
- M1.7 giữ `User` trong tầng service/repository để thực thi domain method và persistence; request không được map mù lên entity, còn mọi đầu ra hồ sơ vẫn đi qua `UserResponseMapper` thành `UserResponse`, không trả JPA entity trực tiếp.
- Đã bổ sung `UserOwnershipPolicy` fail-closed: actor thiếu, owner thiếu hoặc username không khớp đều trả lỗi tài nguyên không tồn tại; `UserProfileService` kiểm tra policy trước cập nhật hồ sơ và đổi mật khẩu.
- ArchUnit cấm mã `identity.internal` phụ thuộc `@PostAuthorize`, tránh phát hiện quyền quá muộn sau mutation. Regression test xác nhận principal chỉ sửa đúng hồ sơ của mình; STAFF không thể khóa/đổi role STAFF khác và request bị từ chối không làm đổi status, role hoặc session của target.
- `mvnw.cmd spotless:check clean verify` sau M1.7 thành công với 76 test, 0 failure, 0 error, 0 skipped; MySQL 8.0.46 Testcontainers, Flyway V1–V8, Spring Modulith, ArchUnit, Spotless và JaCoCo đều đạt. `docker compose config --quiet` hợp lệ.
- PR số `7` vượt hai required check `verify` và `dependency-review`, được merge qua branch protection. Workflow `verify` của merge commit `3b8f846` tiếp tục thành công; local `main` và `origin/main` đồng bộ, nhánh feature đã được xóa.
- M2.1 tạo aggregate `Category` và `Product` sở hữu `ProductVariant`; khóa các bất biến category hợp lệ, SKU duy nhất, giá không âm, mã tiền ISO 4217, trạng thái publish/hide/archive, soft-delete, audit fields và optimistic locking ở cả domain lẫn database.
- Flyway V9 tạo ba bảng `san_pham_danh_muc`, `san_pham_san_pham`, `san_pham_bien_the`; test xác nhận chạy được từ schema rỗng, nâng cấp từ V8 không mất dữ liệu và tương thích MySQL 8.0.46.
- Quality gate M2.1 thành công với 101 test, 0 failure, 0 error, 0 skipped; instruction coverage 90,66%, branch coverage 72,59%; Spotless, Modulith, ArchUnit, H2, MySQL và Docker Compose đều đạt.
- PR số `10` vượt hai required check `verify` và `dependency-review`, được merge thành commit `a32cd3e`; workflow `verify` của merge commit tiếp tục thành công. README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- M2.2 bổ sung API quản trị category, product và variant theo đúng lớp controller-service-mapper-DTO; request không binding JPA entity và response không trả entity trực tiếp. API tạm thời fail-closed bằng `ROLE_ADMIN`; public Catalog và permission chi tiết vẫn được giữ đúng phạm vi M2.6.
- Optimistic locking kiểm tra `version` ở request và tiếp tục dựa trên JPA `@Version` ở database; unique race của category code/slug, product slug và SKU được chuyển thành lỗi nghiệp vụ ổn định. Luồng publish/hide, category đang được sử dụng và variant hoạt động cuối cùng đều có regression test.
- Phân trang Catalog chỉ nhận sort allowlist `CREATED_AT`, `UPDATED_AT`, `NAME`, luôn thêm `id ASC` làm tie-breaker và escape `!`, `%`, `_` trong keyword. Truy vấn aggregate và phân trang đã chạy thực tế trên MySQL 8.0.46, không chỉ H2.
- Quality gate M2.2 sau đợt dọn cảnh báo Sonar thành công với 111 test, 0 failure, 0 error, 0 skipped; instruction coverage 90,60%, line coverage 89,60%, branch coverage 71,27%; Spotless, Modulith, ArchUnit, OpenAPI, H2, MySQL và Docker Compose đều đạt.
- PR số `11` vượt hai required check `verify` và `dependency-review`, được merge thành commit `946d93f`; workflow `verify` của merge commit tiếp tục thành công. README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- PR số `12` xử lý toàn bộ 13 cảnh báo Sonar có thể sửa an toàn, giảm báo cáo toàn bộ `src` từ 21 xuống 8 cảnh báo đã được thẩm định là ngoại lệ thư viện/framework hoặc package giữ chỗ. Hai required check đều đạt, PR được merge thành commit `b2aba17` và workflow `verify` của merge commit tiếp tục thành công.
- M2.3 tạo vertical slice `catalog.internal.image`, tách `ObjectStorage` port khỏi filesystem adapter và fake memory cho test; metadata dùng bảng `san_pham_hinh_anh`, không lưu binary/Base64 trong MySQL hoặc response.
- Upload kiểm tra giới hạn file/sản phẩm, MIME + magic bytes + decode ảnh + số pixel; object key dùng UUID, tên client được làm sạch. Pessimistic lock bảo vệ thứ tự/ảnh đại diện; object đã lưu được dọn khi transaction rollback và object bị xóa chỉ sau khi database commit.
- Kiểm tra dữ liệu MySQL 8.0.46 xác nhận Flyway V1–V10, bốn bảng `san_pham_*` và metadata thực tế gồm MIME, số byte, ảnh đại diện, thứ tự. Test lỗi giữa upload xác nhận cả object storage và bảng metadata đều không còn dữ liệu mồ côi.
- Quality gate M2.3 thành công với 127 test, 0 failure, 0 error, 0 skipped; instruction coverage 90,39%, line coverage 89,59%, branch coverage 72,10%; Spotless, Modulith, ArchUnit, OpenAPI, H2, MySQL và Docker Compose đều đạt. Sonar toàn bộ source giữ nguyên baseline 8 cảnh báo/6 file, không có cảnh báo mới từ M2.3.
- Bộ dữ liệu kiểm thử trực quan local ngày 30/09/2026 gồm 500 tài khoản người dùng mẫu, 20 danh mục nghiệp vụ, 500 sản phẩm, 1.000 biến thể và 500 ảnh minh họa; mỗi sản phẩm có đúng hai biến thể và một ảnh đại diện. Đây là dữ liệu demo local, không phải migration hoặc dữ liệu production.
- Cổng dữ liệu MySQL 8.0.46 sau khi nạp mẫu xác nhận Flyway V10; số bản ghi mồ côi, khóa nghiệp vụ trùng, user thiếu role, họ-tên/email trùng, sản phẩm sai số biến thể/ảnh chính, timestamp ngược và metadata ảnh không hợp lệ đều bằng 0. Kho object storage có 500 file, không có file tạm; smoke test qua API trả 500 sản phẩm, 50 trang khi dùng kích thước trang 10; user mẫu đăng nhập và logout thành công.
- M2.4 dùng MinIO Java SDK 9.0.3 cho adapter S3-compatible. Do MinIO Community Server đã ngừng phát hành image chính thức, local Compose dùng Silo `RELEASE.2026-08-06T00-00-00Z`, fork tương thích MinIO đang được duy trì; production phải chọn dịch vụ S3/MinIO được tổ chức phê duyệt, không mặc định kế thừa lựa chọn container local.
- Bản sao lưu trước khi thay dữ liệu demo nằm tại `C:\Users\minhp\AppData\Local\Temp\shop-backups\shop-before-realistic-seed-20260930.sql`, SHA-256 `86255D2915FAD1219FB48F1DF764E81B22A3A399D1CA625EE3EFE835733E6279`.
- PR số `13` vượt hai required check `verify` và `dependency-review`, được merge thành commit `d8b536d`; workflow CI số `36` của merge commit trên `main` tiếp tục thành công. Nhánh feature local/remote đã được xóa, còn README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.
- PR số `15` vượt hai required check `verify` và `dependency-review`, được merge thành commit `aa5effc`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh `feature/minio/tich-hop-object-storage-local` local/remote đã được xóa; README tiến độ vẫn chỉ lưu local.
- M2.5 tách `ProductSearchQuery` khỏi service: MySQL production dùng FULLTEXT kết hợp B-tree prefix, còn H2 test dùng JPQL `LIKE` fallback; cả hai cùng giữ contract phân trang và hydrate aggregate qua JPA mapper thay vì trả native row.
- Keyword được chuẩn hóa Unicode NFKC, strip/gộp khoảng trắng, lowercase và escape `!`, `%`, `_`; Boolean FULLTEXT chỉ được dựng từ token chữ/số do server kiểm soát. Sort dùng enum allowlist và `id` làm tie-breaker nên không đưa input client vào SQL.
- Flyway V11 tách riêng migration MySQL/H2; MySQL thêm FULLTEXT `name,slug` và B-tree cho ba kiểu sort cùng filter category/status. Migration local giữ nguyên 20 category, 500 product, 1.000 variant và 500 image; không có migration thất bại hay bảng benchmark còn sót.
- Benchmark MySQL 8.0.46 với 50.000 product/100.000 variant đạt warm run khoảng 50,3 ms cho page + count; các nhánh bảng gốc dùng FULLTEXT/range/index lookup. Stored procedure chưa đem lại lợi ích nên không được thêm vào.
- Quality gate phạm vi M2.5 đạt 20 test H2/migration/architecture, 2 test MySQL search và 5 test tương thích MySQL; Spotless, `git diff --check` và Docker Compose đều đạt. Sonar cùng full regression thủ công tiếp tục để quality gate đóng M2 theo quy ước.
- PR số `16` vượt hai required check `verify` và `dependency-review`, được merge thành commit `f66da7b`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Nhánh `feature/tim-kiem/toi-uu-query-index-catalog` local/remote đã được xóa; README tiến độ vẫn chỉ lưu local.
- M2.6 tách `/api/catalog` công khai khỏi `/api/admin/catalog`; public request không nhận trạng thái và service luôn cưỡng chế `PUBLISHED`. Response storefront chỉ chứa dữ liệu bán hàng an toàn, lọc biến thể không hoạt động và không lộ version/trạng thái/object key/tên file/audit timestamp.
- Flyway V12 bổ sung `CATALOG_READ`, `CATALOG_WRITE`, `CATALOG_PUBLISH`, `CATALOG_IMAGE_MANAGE`, gán cho `ADMIN`/`STAFF` và revoke refresh session cũ để token mới mang đúng permission. Architecture test bắt buộc mọi endpoint quản trị Catalog khai báo `@PreAuthorize` theo `CATALOG_*`, không quay lại hard-code role.
- Cổng dữ liệu MySQL 8.0.46 nâng local từ V11 lên V12 và giữ nguyên 20 category, 500 product, 1.000 variant, 500 image; 10 permission/19 liên kết role-permission đúng kỳ vọng, không còn refresh session hoạt động. Smoke test công khai trả đúng 460 product publish trong tổng 500 và không lộ trường nội bộ.
- SonarQube for IDE đã quét toàn bộ source ở thời điểm đóng M2. Năm cảnh báo có thể sửa trong phần kiểm chứng M2.6 gồm một assertion kiến trúc và bốn assertion query-plan đã được xử lý; các cảnh báo còn lại thuộc baseline/framework đã thẩm định, không phát sinh cảnh báo trong storefront hoặc permission mới.
- Quality gate đóng M2 đạt 141 test, 0 failure, 0 error, 0 skipped; instruction coverage 90,91%, line coverage 90,24%, branch coverage 73,29%; Spotless, Spring Modulith, ArchUnit, OpenAPI, Flyway V1–V12, H2, MySQL 8.0.46, MinIO Testcontainers, `git diff --check` và Docker Compose đều đạt.
- PR số `17` vượt hai required check `verify` và `dependency-review`, được merge thành commit `8b41f00`; local `main` đã đồng bộ với `origin/main` và nhánh `feature/phan-quyen/hoan-thien-catalog` đã được xóa local/remote. README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.

- M3.1 bổ sung aggregate tồn kho theo SKU/location với `onHand`, `reserved`, `available` dẫn xuất và optimistic version; `StockMovement` được đánh dấu immutable, còn `StockReservation` có định danh ngoài, trạng thái và TTL.
- Flyway V13 tạo ba bảng tiếng Việt `ton_kho_mat_hang`, `ton_kho_bien_dong`, `ton_kho_giu_hang`, các unique/check/index/foreign key nội bộ; không tạo foreign key sang Catalog. Migration cũng thêm `INVENTORY_READ`/`INVENTORY_WRITE` cho ADMIN/STAFF và revoke refresh session cũ.
- Inventory chỉ gọi named interface `catalog :: inventory`; Spring Modulith và ArchUnit đã xác nhận không truy cập entity/repository/internal package của Catalog. API admin hỗ trợ tìm kiếm, xem chi tiết, khởi tạo, điều chỉnh optimistic và xem sổ biến động; reserve command được giữ đúng phạm vi M3.2 để chưa tạo lỗ hổng oversell.
- Quality gate M3.1 đạt 162 test, 0 failure, 0 error, 0 skipped trên H2, MySQL 8.0.46 và MinIO Testcontainers; instruction coverage 91,16%, line coverage 90,52%, branch coverage 72,72%; Maven verify, Spotless, Spring Modulith, ArchUnit, OpenAPI, Flyway V1–V13, `git diff --check` và Docker Compose đều đạt. Sonar tiếp tục chạy khi đóng M3 theo quy ước.
- PR số `18` vượt hai required check `verify` và `dependency-review`, được merge thành commit `2e347d0`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Local `main` đã đồng bộ với `origin/main`, nhánh `feature/ton-kho/xay-dung-nen-tang` đã được xóa local/remote; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.

- M3.2 mở named interface `inventory :: reservation` cho module Order tương lai; reserve dùng atomic conditional update trên `reserved_quantity`, còn reservation, movement và balance event cùng nằm trong một transaction `REQUIRES_NEW` có timeout.
- Retry chỉ áp dụng hữu hạn cho lỗi tranh chấp lock; thiếu hàng, không tìm thấy stock item, expiration sai và trùng reservation là lỗi nghiệp vụ không retry. Metrics ghi outcome và số lần lock retry; cấu hình TTL/timeout/attempt/backoff có default và validation giới hạn.
- Test MySQL 8.0.46 chứng minh hai transaction tranh đơn vị tồn cuối chỉ một request thành công, request còn lại nhận lỗi thiếu hàng, số dư không âm và chỉ có một reservation/movement. Test tích hợp có teardown dữ liệu đã commit để không phụ thuộc thứ tự chạy toàn suite.
- Quality gate M3.2 đạt 173 test, 0 failure, 0 error, 0 skipped; instruction coverage 91,07%, line coverage 90,28%, branch coverage 72,04%; Maven `clean verify`, Spotless, Spring Modulith, ArchUnit, Flyway V1–V13, H2, MySQL 8.0.46, `git diff --check` và Docker Compose đều đạt. Sonar tiếp tục chạy khi đóng M3 theo quy ước.
- PR số `19` vượt hai required check `verify` và `dependency-review`, được merge thành commit `51113a4`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Local `main` đã đồng bộ với `origin/main`, nhánh `feature/ton-kho/giu-hang-nguyen-tu` đã được xóa local/remote; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.

- M3.3 hoàn thiện state machine `RESERVED -> CONFIRMED|RELEASED|EXPIRED`; confirm giảm đồng thời `onHand/reserved`, release và expiration chỉ giảm `reserved`, mỗi transition append đúng một movement và phát balance/status event trong transaction.
- Lifecycle luôn khóa reservation trước stock item; mọi item expiration chạy bằng transaction `REQUIRES_NEW`. Nhiều instance có thể cùng chọn candidate nhưng kiểm tra lại trạng thái dưới row lock nên chỉ một instance trả kho; lỗi một item không rollback cả batch.
- Scheduler có enable/fixed-delay/initial-delay/batch-size qua biến môi trường, metric có tag cardinality hữu hạn và được bật ở application root thay vì phụ thuộc cấu hình Security của Identity. Không thêm migration vì Flyway V13 đã có status, movement type và index `(status, expires_at)`.
- Quality gate M3.3 đạt 190 test, 0 failure, 0 error, 0 skipped; instruction coverage 91,47%, line coverage 90,82%, branch coverage 72,05%; Maven `clean verify`, Spotless, Spring Modulith, ArchUnit, Flyway V1–V13, H2, MySQL 8.0.46, MinIO Testcontainers, `git diff --check` và Docker Compose đều đạt. Sonar tiếp tục chạy khi đóng M3 theo quy ước.
- PR số `20` vượt hai required check `verify` và `dependency-review`, được merge thành commit `23b7c63`; workflow `verify` của merge commit trên `main` tiếp tục thành công. Local `main` đã đồng bộ với `origin/main`, nhánh `feature/ton-kho/vong-doi-giu-hang` đã được xóa local/remote; README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.

- M3.4 hoàn thiện idempotency theo khóa `(reservationId, operation)` và fingerprint payload; retry cùng payload replay đúng kết quả đã commit, payload khác bị từ chối, còn movement và balance không bị mutation lần hai. Flyway V14 lưu kết quả idempotency đầy đủ và tương thích dữ liệu cũ.
- M3.5 bổ sung stress test MySQL 8.0.46 với 24 worker, 5 vòng deterministic và barrier xuất phát đồng thời; test kiểm chứng reserve, confirm, release và giữ nguyên reservation không thể làm `available` âm hoặc `confirmed + reserved` vượt tồn ban đầu.
- Quality gate đóng M3 đạt 205 test, 0 failure, 0 error, 0 skipped; instruction coverage 91,33%, line coverage 90,79%, branch coverage 71,92%; Maven `clean verify`, Spotless, Spring Modulith, ArchUnit, Flyway V1–V14, H2, MySQL, MinIO Testcontainers, Sonar, dependency/security, `git diff --check` và Docker Compose đều đạt.
- PR số `22` vượt hai required check `verify` và `dependency-review`, được merge thành commit `f01f2c8`. README tiến độ vẫn chỉ lưu local và được `.gitignore` loại trừ.

Toàn bộ M0.1–M0.8, M1.1, M1.2, M1.3, M1.4A–M1.4H, M1.5, M1.6, M1.6A, M1.7, M1.8, M2.1–M2.6, M3.1–M3.5, M4.1–M4.7, M5.1–M5.2, M6.2A.1 và M6.9A đã vượt quality gate phạm vi tương ứng. Milestone M4 đã đóng; M5.2 đã merge qua PR số `31`. M5.3 đã vượt toàn bộ gate local trên nhánh feature nhưng còn chờ smoke test Stripe sandbox thật nên chưa mở PR/merge.

## 8. Luồng hoạt động toàn dự án

### 8.1. Từ xác thực đến checkout

```text
Client
  → Identity: đăng ký/đăng nhập, CAPTCHA thích ứng, rate limit
  → JWT: access token mang role/permission; refresh token được hash, rotation và revoke theo family
  → Catalog: xem danh mục, sản phẩm, biến thể và ảnh đang được phép bán
  → Cart: thêm/sửa/xóa SKU; server kiểm tra SKU và giới hạn số lượng
  → Checkout + Idempotency-Key
      → Order đọc giỏ hàng hiện tại
      → Catalog cung cấp lại giá/currency phía server
      → Order lưu PENDING order và snapshot từng dòng
      → Inventory reserve nguyên tử theo reservationId và TTL
```

Client không được gửi giá đáng tin cậy, tự gán role, tự đổi trạng thái Order/Payment hoặc xác nhận tồn kho. Controller chỉ nhận contract; service điều phối use case; entity bảo vệ invariant; repository chỉ thuộc module sở hữu dữ liệu.

### 8.2. Thanh toán và hoàn tất đơn

```text
Order orchestration
  → Payment tạo attempt + provider idempotency key
  → Provider adapter tạo hosted checkout hoặc QR động
  → Client thanh toán trên trang/app của provider
  → Provider gửi webhook có chữ ký
  → Payment xác minh chữ ký + chống event lặp + cập nhật state machine
      → SUCCEEDED: phát event → Inventory confirm → Order PAID
      → FAILED/CANCELLED/EXPIRED: phát event → Inventory release → Order PAYMENT_FAILED/EXPIRED
      → UNKNOWN/PENDING: reconciliation hỏi lại provider, không tự suy diễn thành công
```

Return/cancel trên trình duyệt và polling chỉ phục vụ giao diện, không phải bằng chứng thanh toán. Implementation M5.3 đã dựng hosted checkout/return an toàn nhưng còn chờ smoke test Stripe sandbox; webhook, event cập nhật Order/Inventory, reconciliation và QR lần lượt thuộc M5.4–M5.7 nên sơ đồ trên là luồng đích, không phải tuyên bố mọi bước đã triển khai.

### 8.3. Audit, độ tin cậy và tách microservices

- Identity ghi lịch sử truy cập append-only; không lưu password, token thô, CAPTCHA answer hoặc payment secret.
- Correlation ID đi xuyên request/event để truy vết. Error API dùng `ApiResponse<T>` và message tiếng Việt từ `message.properties`.
- M6 bổ sung outbox/inbox, observability, performance, backup/restore và purchase-flow end-to-end. State cùng outbox được commit một transaction; publisher/consumer có retry và idempotency.
- Chỉ sau cổng dữ liệu cuối M6 mới tách M7 theo thứ tự Gateway → Identity → Catalog → Inventory → Payment → Order. Contract nội bộ được thay bằng HTTP/event theo từng bước, không dùng distributed database transaction.
- Mỗi service sở hữu database/schema của mình; state machine, idempotency, event, reconciliation và compensation xử lý lỗi giữa các service.

### 8.4. Thanh toán QR kiểu sàn thương mại điện tử

QR đúng cho Shop là **QR động theo từng payment attempt**, không phải ảnh QR tài khoản ngân hàng cố định:

1. Backend khóa amount/currency/order reference, tạo idempotency key rồi gọi API merchant của provider.
2. Provider trả `providerReference`, QR payload hoặc hosted QR URL và `expiresAt`; Shop lưu metadata tối thiểu, frontend dựng QR/hiển thị URL mà không nhận merchant secret.
3. Người mua quét bằng ứng dụng ngân hàng/ví. Trang frontend có thể polling trạng thái nhưng kết quả polling chỉ để cập nhật UX.
4. Provider gọi webhook; backend kiểm tra chữ ký, timestamp/replay window, amount/currency/reference và deduplicate event trước khi đổi trạng thái.
5. Nếu webhook mất hoặc trạng thái còn `UNKNOWN/PENDING`, reconciliation hỏi API provider. QR hết hạn chuyển `EXPIRED` và release tồn kho đúng một lần.

Stripe hosted checkout của M5.3 là adapter đầu tiên để ổn định payment port. M5.7 sẽ cắm provider QR phù hợp thị trường qua cùng port; nhà cung cấp cụ thể chỉ được chọn sau khi có tài khoản merchant, tài liệu sandbox, phí và yêu cầu pháp lý. Cách này giống trải nghiệm Shopee ở mức luồng nghiệp vụ nhưng không sao chép hay phụ thuộc API nội bộ của Shopee.

Không sử dụng distributed database transaction. Luồng thất bại được xử lý bằng state machine, idempotency, event, reconciliation và compensation.

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

Chạy cả ứng dụng bằng Docker Compose (service `app` dùng profile riêng nên không ảnh hưởng lệnh chỉ khởi động hạ tầng):

```powershell
Copy-Item .env.example .env
docker compose --profile app up -d --build
```

Compose tự đọc `.env`, nhưng chỉ truyền vào container các biến được khai báo tường minh trong `compose.yaml`. `.env` đã được loại khỏi Git và Docker build context. Để chuyển từ provider giả lập sang Stripe sandbox, điền `STRIPE_SECRET_KEY`, `STRIPE_RETURN_STATE_SECRET`, sau đó đặt `PAYMENT_PROVIDER_TYPE=stripe` và tạo lại container `app`. Profile local đã mặc định callback tại `http://localhost:8080/api/payments/checkout/return|cancel` và allowlist `localhost,127.0.0.1`; ngoại lệ HTTP này chỉ hoạt động với `sk_test_`. Staging/production vẫn phải override bằng URL HTTPS và allowlist hostname tương ứng.

```powershell
docker compose --profile app up -d --build --force-recreate app
docker compose logs -f app
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
