# Danh mục sự kiện audit và lịch sử truy cập

## 1. Trạng thái tài liệu

- Phiên bản: `1.0`
- Trạng thái: Accepted cho M6.9A
- Module sở hữu dữ liệu đích: `audit`
- Phạm vi hiện tại: định nghĩa sự kiện; chưa tạo entity, bảng, API hoặc cơ chế ghi

`action_code` trong tài liệu này là định danh máy đọc ổn định. Nhãn tiếng Việt trên giao diện phải được ánh xạ từ message bundle và có thể thay đổi mà không sửa dữ liệu lịch sử.

## 2. Access log và audit event là hai loại dữ liệu khác nhau

| Loại | Mục đích | Nơi lưu | Ví dụ | Không dùng để |
| --- | --- | --- | --- | --- |
| Access log kỹ thuật | Quan sát lưu lượng, latency, status và lỗi vận hành của mọi request | Structured log/observability, retention ngắn | `GET /actuator/health`, tải Swagger, đọc sản phẩm công khai | Làm lịch sử nghiệp vụ lâu dài |
| Audit event | Chứng minh ai/hệ thống nào đã thực hiện hành động bảo mật hoặc nghiệp vụ quan trọng và kết quả ra sao | Kho append-only của module `audit` | đăng nhập, đổi quyền, khóa user, đổi trạng thái đơn, hoàn tiền | Thay thế access log hoặc lưu nguyên request/response |

Không ghi vào database audit đối với health check, static asset, Swagger, request đọc công khai, đọc hồ sơ của chính người dùng, introspection nội bộ lặp lại hoặc từng lần CAPTCHA được phát hành. Truy cập dữ liệu nhạy cảm như xem lịch sử audit, tải export hoặc xem dữ liệu thanh toán vẫn phải tạo audit event.

## 3. Quy ước hợp đồng

### 3.1 Mã hành động

- Dạng chuẩn: `<MODULE>.<FEATURE>.<ACTION>`, chỉ dùng chữ hoa ASCII, số và dấu chấm.
- `outcome` là trường riêng; không tạo các mã `_SUCCESS`, `_FAILED` khác nhau cho cùng một hành động.
- Mã đã phát hành không được đổi nghĩa hoặc tái sử dụng. Nếu semantics thay đổi không tương thích thì tạo mã mới.
- `feature` và nhãn hiển thị không được dùng làm khóa tra cứu.

### 3.2 Chủ thể và kết quả

| Trường | Giá trị tối thiểu | Quy tắc |
| --- | --- | --- |
| `actor_type` | `USER`, `ANONYMOUS`, `SYSTEM`, `SERVICE` | `ANONYMOUS` dùng cho đăng nhập thất bại khi chưa xác định được user; `SERVICE` dành cho ranh giới microservice sau này. |
| `actor_user_id` | UUID hoặc `null` | Chỉ có giá trị khi danh tính đã được xác thực/chắc chắn; không tra cứu ngược username thất bại để tránh account enumeration. |
| `actor_username_snapshot` | chuỗi chuẩn hóa hoặc `null` | Lưu snapshot cho user đã biết; đăng nhập thất bại chỉ lưu username đã redaction/hashing theo chính sách M6.9D. |
| `outcome` | `SUCCESS`, `FAILURE`, `DENIED` | `FAILURE` là xử lý không hoàn tất do lỗi nghiệp vụ/kỹ thuật; `DENIED` là bị từ chối bởi authentication/authorization/policy. |

### 3.3 Mức nhạy cảm và retention baseline

| Lớp | Baseline | Áp dụng | Quy tắc |
| --- | ---: | --- | --- |
| `SECURITY_400D` | 400 ngày | đăng nhập, session, credential | IP/user-agent là dữ liệu cá nhân; chỉ người có `AUDIT_READ_SENSITIVE` được xem đầy đủ. |
| `PRIVILEGED_5Y` | 5 năm | quản trị user/role/quyền, đọc audit | Giữ bằng chứng thay đổi quyền đặc biệt và truy cập dữ liệu nhạy cảm. |
| `MASTER_DATA_3Y` | 3 năm | product/catalog | Giữ timeline thay đổi dữ liệu chủ sau khi bản ghi bị ẩn/xóa mềm. |
| `COMMERCE_10Y` | 10 năm | order, inventory, payment/refund | Là baseline thận trọng cho dữ liệu giao dịch; phải được pháp chế/kế toán xác nhận trước production. |
| `OPERATIONAL_180D` | 180 ngày | system job và xử lý nền không liên quan giao dịch | Có thể tổng hợp/archive sớm hơn nếu vẫn giữ được bằng chứng điều tra. |

Legal hold luôn thắng cleanup. M6.9I sẽ biến các baseline thành cấu hình retention/archive và chốt lại theo yêu cầu pháp lý thực tế.

## 4. Danh mục sự kiện phiên bản 1

Ký hiệu mức nhạy cảm: `R` = restricted, `C` = confidential, `I` = internal.

Module sở hữu được xác định ổn định từ prefix của `action_code`:

| Prefix | Module sở hữu |
| --- | --- |
| `IDENTITY` | `identity` |
| `CATALOG` | `catalog` |
| `ORDER` | `order` |
| `INVENTORY` | `inventory` |
| `PAYMENT` | `payment` |
| `AUDIT` | `audit` |
| `EXPORT` | `export` (module điều phối export tương lai; source module vẫn quyết định data scope) |
| `SYSTEM` | `platform` hoặc module hạ tầng được ghi rõ bởi `feature` (`AUDIT`, `OUTBOX`, `JOB`) |

Owner phát event qua public audit port và không được truy cập repository của module `audit`.

### 4.1 Identity, authentication và quản trị quyền

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `IDENTITY.USER.REGISTER` | Sau khi transaction đăng ký commit hoặc bị từ chối | `ANONYMOUS`, `USER` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `SECURITY_400D` | Điều tra đăng ký giả mạo, spam và conflict định danh. |
| `IDENTITY.AUTH.LOGIN` | Cuối luồng xác thực password/CAPTCHA | `ANONYMOUS`, `USER` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `SECURITY_400D` | Lịch sử truy cập và điều tra brute force; failure reason dùng mã chung, không lộ user tồn tại. |
| `IDENTITY.AUTH.REFRESH` | Sau rotation/replay decision | `USER` | `SESSION` | `SUCCESS`, `FAILURE`, `DENIED` | R / `SECURITY_400D` | Truy vết vòng đời session và token replay mà không lưu token thô. |
| `IDENTITY.AUTH.LOGOUT` | Sau revoke family, kể cả idempotent | `USER` | `SESSION` | `SUCCESS`, `FAILURE`, `DENIED` | R / `SECURITY_400D` | Chứng minh thời điểm người dùng chủ động kết thúc phiên. |
| `IDENTITY.SESSION.REVOKE` | Sau revoke do đổi password/status/role hoặc quản trị | `USER`, `SYSTEM` | `SESSION`, `USER` | `SUCCESS`, `FAILURE` | R / `SECURITY_400D` | Liên kết nguyên nhân thu hồi với tài khoản và phiên bị tác động. |
| `IDENTITY.CREDENTIAL.PASSWORD_CHANGE` | Sau transaction đổi password | `USER` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `SECURITY_400D` | Phát hiện chiếm quyền tài khoản; chỉ lưu lý do và metadata, không lưu password/hash. |
| `IDENTITY.CREDENTIAL.PASSWORD_RESET` | Luồng reset tương lai sau khi token một lần được xử lý | `ANONYMOUS`, `USER`, `SYSTEM` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `SECURITY_400D` | Bằng chứng khôi phục tài khoản; không lưu reset token. |
| `IDENTITY.PROFILE.UPDATE` | Sau transaction cập nhật hồ sơ | `USER` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | C / `PRIVILEGED_5Y` | Truy vết thay đổi PII; detail chỉ chứa tên trường thay đổi, không lưu giá trị nhạy cảm. |
| `IDENTITY.USER.STATUS_CHANGE` | Service quản trị sau khi đổi trạng thái và revoke session | `USER` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Chứng minh ai khóa/mở/vô hiệu hóa tài khoản, trạng thái trước/sau và lý do chuẩn hóa. |
| `IDENTITY.USER.ROLE_REPLACE` | Service quản trị sau khi thay role và revoke session | `USER` | `USER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Thay đổi đặc quyền phải có actor, role trước/sau và target. |
| `IDENTITY.ROLE.CREATE` | Sau transaction tạo custom role | `USER` | `ROLE` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Role mới có thể mở rộng phạm vi truy cập. |
| `IDENTITY.ROLE.UPDATE` | Sau transaction đổi description/permission | `USER` | `ROLE` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Truy vết permission trước/sau và session bị revoke. |
| `IDENTITY.ROLE.DELETE` | Sau transaction xóa custom role | `USER` | `ROLE` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Chứng minh role nào bị xóa và vì sao request bị từ chối nếu role đang dùng. |

Không có `PERMISSION.CREATE/UPDATE/DELETE` trong v1 vì permission là capability do code/Flyway sở hữu, không phải dữ liệu do API quản trị tự tạo.

### 4.2 Catalog/product

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `CATALOG.PRODUCT.CREATE` | Sau transaction tạo product/variant | `USER`, `SERVICE` | `PRODUCT` | `SUCCESS`, `FAILURE`, `DENIED` | C / `MASTER_DATA_3Y` | Nguồn gốc dữ liệu sản phẩm và SKU. |
| `CATALOG.PRODUCT.UPDATE` | Sau transaction cập nhật field cho phép | `USER`, `SERVICE` | `PRODUCT` | `SUCCESS`, `FAILURE`, `DENIED` | C / `MASTER_DATA_3Y` | Timeline thay đổi nội dung, giá niêm yết hoặc thuộc tính; lưu field diff allowlist. |
| `CATALOG.PRODUCT.STATUS_CHANGE` | Sau publish/unpublish/archive | `USER`, `SERVICE`, `SYSTEM` | `PRODUCT` | `SUCCESS`, `FAILURE`, `DENIED` | C / `MASTER_DATA_3Y` | Giải thích thời điểm sản phẩm xuất hiện/biến mất khỏi kênh bán. |
| `CATALOG.PRODUCT.DELETE` | Sau soft delete | `USER`, `SERVICE` | `PRODUCT` | `SUCCESS`, `FAILURE`, `DENIED` | C / `MASTER_DATA_3Y` | Giữ bằng chứng dù resource không còn hiển thị. |

Đọc/search product thông thường chỉ ghi access log kỹ thuật, không tạo audit event.

### 4.3 Order

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `ORDER.CHECKOUT.SUBMIT` | Kết thúc command checkout idempotent | `USER`, `SERVICE` | `ORDER`, `CART` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Liên kết idempotency key đã hash, order và kết quả checkout. |
| `ORDER.ORDER.STATUS_CHANGE` | Sau state transition hợp lệ | `USER`, `SERVICE`, `SYSTEM` | `ORDER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Chứng minh trạng thái trước/sau và command/event gây chuyển trạng thái. |
| `ORDER.ORDER.CANCEL` | Sau cancel và các bước bù | `USER`, `SERVICE`, `SYSTEM` | `ORDER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Điều tra người/hệ thống hủy, lý do và kết quả release/refund liên quan. |
| `ORDER.ORDER.SENSITIVE_READ` | Khi admin/seller xem dữ liệu order ngoài phạm vi “đơn của tôi” | `USER` | `ORDER` | `SUCCESS`, `DENIED` | R / `PRIVILEGED_5Y` | Audit việc đọc PII/giao dịch nhạy cảm; không áp dụng cho người mua xem đơn của chính mình. |

### 4.4 Inventory

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `INVENTORY.STOCK.RESERVE` | Sau reserve idempotent | `SERVICE`, `SYSTEM` | `STOCK_RESERVATION`, `SKU` | `SUCCESS`, `FAILURE`, `DENIED` | C / `COMMERCE_10Y` | Điều tra oversell và liên kết reservation với order. |
| `INVENTORY.STOCK.RELEASE` | Sau release/expiry | `SERVICE`, `SYSTEM`, `USER` | `STOCK_RESERVATION`, `SKU` | `SUCCESS`, `FAILURE`, `DENIED` | C / `COMMERCE_10Y` | Chứng minh tồn giữ đã được trả do cancel/timeout. |
| `INVENTORY.STOCK.COMMIT` | Sau xác nhận trừ tồn | `SERVICE`, `SYSTEM` | `STOCK_RESERVATION`, `SKU` | `SUCCESS`, `FAILURE` | C / `COMMERCE_10Y` | Đối soát order/payment với biến động tồn kho. |
| `INVENTORY.STOCK.ADJUST` | Sau điều chỉnh thủ công/import | `USER`, `SYSTEM` | `STOCK_ITEM`, `SKU` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Điều chỉnh thủ công là hành động rủi ro cao; cần before/after, reason code và nguồn import. |

### 4.5 Payment

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `PAYMENT.PAYMENT.CREATE` | Sau tạo payment attempt idempotent | `USER`, `SERVICE` | `PAYMENT`, `ORDER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Đối soát order, amount/currency và provider; không lưu PAN/CVV/client secret. |
| `PAYMENT.PAYMENT.STATUS_CHANGE` | Sau state transition từ provider/webhook/job | `SERVICE`, `SYSTEM` | `PAYMENT` | `SUCCESS`, `FAILURE` | R / `COMMERCE_10Y` | Chứng minh trạng thái trước/sau và provider event gây thay đổi. |
| `PAYMENT.REFUND.REQUEST` | Sau yêu cầu hoàn tiền | `USER`, `SERVICE` | `REFUND`, `PAYMENT`, `ORDER` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Truy vết actor, lý do, amount/currency và approval. |
| `PAYMENT.REFUND.STATUS_CHANGE` | Sau provider callback/poll | `SERVICE`, `SYSTEM` | `REFUND` | `SUCCESS`, `FAILURE` | R / `COMMERCE_10Y` | Đối soát vòng đời hoàn tiền. |
| `PAYMENT.WEBHOOK.PROCESS` | Sau signature, dedup và xử lý inbox | `SERVICE` | `PROVIDER_EVENT` | `SUCCESS`, `FAILURE`, `DENIED` | R / `COMMERCE_10Y` | Điều tra signature failure/replay; chỉ lưu provider event ID, không lưu payload thô. |

### 4.6 Đọc audit và export

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `AUDIT.HISTORY.SEARCH` | Sau mỗi request tra cứu history | `USER` | `AUDIT_QUERY` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Danh sách chứa username, IP và metadata nhạy cảm; detail chỉ lưu filter allowlist, không lưu kết quả. |
| `AUDIT.HISTORY.DETAIL_VIEW` | Sau request mở chi tiết một event | `USER` | `AUDIT_EVENT` | `SUCCESS`, `DENIED` | R / `PRIVILEGED_5Y` | Chứng minh ai xem detail nhạy cảm. |
| `EXPORT.DATA.REQUEST` | Sau tạo export job | `USER`, `SERVICE` | `EXPORT_JOB` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Truy vết dataset, phạm vi thời gian, filter, format và giới hạn; không lưu nội dung file. |
| `EXPORT.DATA.DOWNLOAD` | Sau kiểm tra quyền/tải file | `USER`, `SERVICE` | `EXPORT_FILE` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Chứng minh ai đã tải dữ liệu, checksum và thời điểm; không lưu signed URL/token. |

### 4.7 System job

| `action_code` | Điểm phát sinh | Actor | Resource | Outcome | Nhạy cảm / retention | Lý do cần audit |
| --- | --- | --- | --- | --- | --- | --- |
| `SYSTEM.JOB.RUN` | Khi job quan trọng kết thúc | `SYSTEM` | `JOB_RUN` | `SUCCESS`, `FAILURE` | I / `OPERATIONAL_180D` | Lưu job name, run ID, thời gian và counters; heartbeat/no-op không cần audit. |
| `SYSTEM.AUDIT.RETENTION_PURGE` | Sau mỗi batch retention/archive | `SYSTEM` | `AUDIT_PARTITION`, `ARCHIVE_BATCH` | `SUCCESS`, `FAILURE`, `DENIED` | R / `PRIVILEGED_5Y` | Chứng minh phạm vi, policy và số record đã archive/xóa; bản ghi này không được nằm trong batch đang xóa. |
| `SYSTEM.OUTBOX.DEAD_LETTER` | Khi event vượt retry policy | `SYSTEM`, `SERVICE` | `OUTBOX_EVENT` | `FAILURE` | C / `OPERATIONAL_180D` | Điều tra mất đồng bộ giữa module/service mà không lưu payload chứa secret. |

## 5. Detail allowlist tối thiểu

| Nhóm | Được phép | Bị cấm |
| --- | --- | --- |
| Identity/auth | reason code chuẩn hóa, auth method, changed field names, role/status trước-sau, session family ID đã hash | password, password hash, access/refresh/reset token, JWT, CAPTCHA answer, cookie, Authorization header |
| Catalog | field names thay đổi, status trước-sau, SKU/product ID | request/response body thô, nội dung media binary |
| Order/inventory | status/quantity trước-sau, currency, reason code, idempotency key đã hash | địa chỉ/điện thoại/email đầy đủ nếu không thật sự cần, cart/request body thô |
| Payment | amount, currency, provider name, provider event/reference ID đã hash hoặc masked | PAN, CVV, bank credential, provider secret, webhook payload/signature thô |
| Export/audit | dataset code, filter allowlist, row count, file ID/checksum, date range | nội dung file, signed URL, token tải, kết quả truy vấn đầy đủ |
| System job | job code, run ID, counters, duration, error code chuẩn hóa | stack trace/detail chứa secret, environment dump |

`detail_json` phải có `detail_schema` versioned, giới hạn kích thước và đi qua redactor tập trung ở M6.9D. Không service nào được tự serialize request/response vào audit.

## 6. Hợp đồng màn hình lịch sử truy cập

Màn hình học bố cục hữu ích từ dự án tham khảo nhưng không có cột “Đơn vị” vì Shop hiện không có nghiệp vụ đơn vị:

| Cột | Nguồn |
| --- | --- |
| STT | Tính từ `page`, `size` và vị trí trong trang; không lưu database. |
| Tên đăng nhập | `actor_username_snapshot`; có thể masked theo quyền. |
| Thời gian thao tác | `occurred_at` lưu UTC, hiển thị theo timezone người dùng. |
| Phân hệ | Nhãn i18n từ `module`. |
| Chức năng | Nhãn i18n từ `feature`. |
| Hành động | Nhãn i18n từ `action_code`. |
| Kết quả | Nhãn từ `outcome`. |
| Địa chỉ IP | `ip_address`; masked nếu chỉ có `AUDIT_READ`, đầy đủ khi có `AUDIT_READ_SENSITIVE`. |
| Thao tác | Mở detail đã redaction; không có sửa/xóa. |

API tra cứu ở M6.9H sẽ hỗ trợ phân trang số trang giống giao diện tham khảo: `page` zero-based, `size` mặc định 10 và tối đa 100, trả `totalElements`/`totalPages`. Kết quả bắt buộc sắp xếp ổn định `occurred_at DESC, id DESC`; export không dùng API phân trang này. Khi dữ liệu đủ lớn, có thể bổ sung cursor mà không phá hợp đồng page-number hiện tại.

## 7. Đánh giá dự án tham khảo

Giữ lại:

- Bảng hiển thị username, thời gian, phân hệ, chức năng, hành động, IP và chi tiết.
- Lọc theo khoảng ngày nửa mở `[from, to)` và phân trang tại database.
- Phân quyền phạm vi dữ liệu trước khi truy vấn.

Không sao chép nguyên trạng:

- Dùng `LocalDateTime.now()` làm mất ngữ nghĩa UTC và khó test; Shop sẽ dùng `Clock` + `Instant`.
- Tin mọi `X-Forwarded-For` cho phép client giả IP; chỉ trusted proxy mới được cung cấp forwarded header.
- Dùng nhãn hiển thị làm định danh hành động khiến dữ liệu đổi nghĩa khi đổi ngôn ngữ/tên chức năng.
- `JpaRepository` công khai update/delete không bảo đảm append-only.
- Không có `outcome`, correlation ID, resource ID hoặc business event ID nên khó điều tra và chống ghi trùng.
- `noiDungChiTiet` tự do có thể lộ secret/PII.
- Export toàn bộ dữ liệu rồi Base64 trong JSON có thể làm cạn heap; Shop sẽ streaming hoặc chạy export job.

## 8. Điều kiện chuyển sang M6.9B

- Mọi nhóm bắt buộc đã có action code, điểm phát sinh, actor, resource, outcome, mức nhạy cảm, retention, owner và lý do audit.
- Danh mục phân biệt rõ access log với audit event và chỉ rõ các request không ghi database.
- Mã hành động không phụ thuộc nhãn tiếng Việt.
- Mọi action code là duy nhất và đúng pattern.
- Các quyết định UTC, IP trusted proxy, redaction, append-only, idempotency và phân trang đã được khóa làm đầu vào cho các bước sau.
