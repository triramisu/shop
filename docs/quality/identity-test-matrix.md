# Ma trận kiểm thử Identity

## Mục tiêu

Tài liệu này là hồ sơ nghiệm thu cho M1.8. Mỗi test phải bảo vệ một hợp đồng API, quy tắc nghiệp vụ, ranh giới bảo mật hoặc hành vi cạnh tranh dữ liệu; không thêm test chỉ để tăng phần trăm coverage.

## Ma trận API và nghiệp vụ

| Luồng | Thành công | Validation/boundary | Authentication/authorization | Trạng thái, replay và cạnh tranh |
| --- | --- | --- | --- | --- |
| Register | Tạo user, BCrypt, role `USER`, DTO không lộ hash | Từng constraint, username/email trùng không phân biệt hoa thường | Endpoint public nhưng endpoint khác vẫn được bảo vệ | Database unique constraint là lớp bảo vệ cuối; lỗi ghi đồng thời map thành conflict |
| Token | Access/refresh token tách biệt, claim và thời hạn đúng | Credential quá dài, sai mật khẩu và user không tồn tại dùng cùng contract | Tài khoản không active bị từ chối sau khi kiểm tra password | CAPTCHA và rate limit có test riêng; refresh session chỉ lưu token hash |
| Introspect | Access token hợp lệ trả `valid=true` | Refresh token, token sửa nội dung hoặc quá dài trả `valid=false`/contract tương ứng | Token thuộc family đã revoke không còn hợp lệ | Logout và thay đổi quyền có regression test vô hiệu hóa access token cũ |
| Refresh | Rotation tạo cặp token mới | Token sai định dạng hoặc access token bị từ chối | User bị khóa không được refresh | Replay revoke cả family; hai refresh đồng thời trên MySQL chỉ một request thành công |
| Logout | Revoke toàn family và idempotent | Token sai định dạng hoặc sai loại bị từ chối | Access token cũ bị từ chối ngay sau logout | Refresh token cũ không thể rotation lại |
| CAPTCHA | Phát hành, xác minh và consume một lần | Sai/thiếu/hết hạn/không cần CAPTCHA | Unknown username áp dụng cùng chính sách chống dò tài khoản | Ghi failure nguyên tử, serialize issuance, giới hạn capacity và cleanup |
| Rate limit | Cho phép burst cấu hình | Chỉ áp dụng đúng endpoint | Trả `429` theo `ApiResponse`; endpoint khác không bị ảnh hưởng | Fail-closed khi đầy bộ nhớ bucket và cleanup bucket rỗi |
| My info/profile | Chỉ trả/cập nhật profile lấy từ principal | Email, ngày sinh và password invalid bị chặn trước mutation | Thiếu access token trả `401`; ownership kiểm tra trước mutation | Email trùng không đổi dữ liệu; MySQL update và mapper Entity → Response được kiểm chứng |
| Change password | Đổi hash và đăng nhập bằng mật khẩu mới | Sai mật khẩu hiện tại hoặc dùng lại mật khẩu cũ bị từ chối | Chỉ owner đã xác thực được đổi | Thu hồi toàn bộ session; request thất bại không thu hồi session |
| User administration | Tìm kiếm/phân trang/xem chi tiết/đổi status/role | UUID, enum, page/size, role reference và wildcard search | `USER` nhận `403`; hierarchy `ADMIN`/`STAFF` được giữ | Chống tự khóa, bảo vệ `ADMIN`, mutation bị từ chối không đổi role/status/session |
| Role/permission administration | Đọc catalog, CRUD custom role | Role trùng, permission thiếu hoặc permission bảo vệ bị từ chối | Chỉ `ADMIN` có `SYSTEM_ROLE_MANAGE` được mutation | Không xóa system role/role đang dùng; optimistic lock map thành conflict; đổi permission thu hồi session liên quan |

## Các lớp kiểm thử

| Lớp | Bằng chứng chính |
| --- | --- |
| Unit | `AuthenticationRateLimitServiceTests`, `UserOwnershipPolicyTests`, `RoleAdministrationServiceTests`, `JwtTokenServiceTests` |
| Repository/transaction | `IdentityRepositoryIntegrationTests`, `MySqlCompatibilityIntegrationTests` |
| Controller/API contract | Các lớp `*IntegrationTests` dùng MockMvc và kiểm tra status, `ApiResponse`, validation, DTO |
| Security | Token type/claim/signature/issuer/audience, `401`, `403`, ownership, role hierarchy, revoke/replay |
| Migration/architecture | `IdentitySchemaMigrationTests`, `AdministrationRoleRenameMigrationTests`, `IdentityLayerArchitectureTests`, `ModularArchitectureTests` |

## Quy tắc dữ liệu test

- `IdentityApiTestClient` gom đăng ký, đăng nhập, bearer header và token payload dùng chung.
- Mỗi test API dùng username/email riêng; các repository/admin test có transaction rollback để không phụ thuộc thứ tự chạy.
- Test cạnh tranh dữ liệu dùng latch/future có timeout; không dùng `sleep` để suy đoán timing.
- H2 dùng cho feedback nhanh; các hành vi khóa, migration và SQL quan trọng được chạy lại trên MySQL 8 Testcontainers.
- Test không chứa credential production hoặc token lấy từ môi trường thật.

## Quality gate

```powershell
.\mvnw.cmd --batch-mode spotless:check clean verify
docker compose config --quiet
```

Điều kiện hoàn thành:

- Không có test fail/error/skipped ngoài trường hợp được tài liệu hóa rõ.
- Flyway chạy từ schema rỗng trên H2 và MySQL 8.
- Spring Modulith và ArchUnit không phát hiện vi phạm ranh giới.
- JaCoCo đạt tối thiểu 85% line và 65% branch toàn bundle.
- Pull request vượt `verify` và `dependency-review` trước khi merge.

## Kết quả nghiệm thu cục bộ

Ngày 28/09/2026, quality gate được chạy từ trạng thái `clean` với Docker Desktop và MySQL 8.0.46:

- 91 test đã chạy; 0 failure, 0 error, 0 skipped.
- Flyway áp dụng thành công đủ V1–V8 trên cả H2 và MySQL 8.
- Spotless, Spring Modulith và 8 luật ArchUnit đều đạt.
- JaCoCo đạt 92,17% line (1.024/1.111) và 73,91% branch (255/345), cao hơn ngưỡng bắt buộc.
- `docker compose config --quiet` và `git diff --check` đều thành công.
- Không có `@Disabled`, `Thread.sleep`, `TODO` hoặc `FIXME` trong test và tài liệu nghiệm thu.

Kết quả trên là bằng chứng cục bộ. Nhiệm vụ chỉ được merge sau khi hai check bắt buộc `verify` và `dependency-review` trên pull request cùng đạt.
