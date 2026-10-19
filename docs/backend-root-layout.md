# ROOT-01 — Backend chạy trực tiếp từ gốc repo

## 1. Mục tiêu và phạm vi

Theo yêu cầu người dùng, bỏ lớp thư mục `D:/shop/backend` bằng cách chuyển toàn bộ Spring project về `D:/shop`. UI vẫn độc lập tại `D:/shop-ui`. Task này chỉ đổi layout, đường dẫn build/deploy và tài liệu; không đổi nghiệp vụ, API, schema, dependency version hoặc dữ liệu.

## 2. Sở hữu và phụ thuộc

Repo `shop` sở hữu `src/`, `pom.xml`, Maven wrapper, `.mvn/`, `ci/`, Dockerfile và cấu hình triển khai backend. `shop-ui` giữ React/nginx và Dockerfile riêng. CI chạy Maven tại gốc, CD build context gốc; Compose lab cần source UI bên cạnh hoặc `SHOP_UI_CONTEXT` tùy chỉnh.

## 3. Luồng và bất biến

Kiểm tra không trùng tên trước khi chuyển; dừng nếu có nguy cơ ghi đè. So sánh SHA-256 toàn bộ file trong `backend/` trước/sau. Chỉ gỡ thư mục `backend` khi đã trống. Giữ nguyên coordinate Maven, tên jar, image repository và env API. Không di chuyển/gộp `target/` hoặc `data/` đang có ở gốc; output build trong `target/` được tái tạo theo Maven gate, không xóa dữ liệu ứng dụng trong `data/`.

## 4. Bảo mật và vận hành

Không đọc/in secret thật hoặc tác động container/database đang chạy. Docker build context mở rộng về gốc nên `.dockerignore` phải loại `.git`, env thật, runtime data, build output và thư mục công cụ không cần thiết. Không nới quyền truy cập, không tạo remote, push/merge hoặc deploy. Bảo toàn thay đổi có sẵn, đặc biệt file skill đang được người dùng stage.

## 5. Đầu ra

Source Java nguyên vẹn tại `shop/src`; Maven/Dockerfile hoạt động tại gốc. Cập nhật CI/CD, Compose local/lab, README hai project, đường dẫn instruction liên quan và kế hoạch website. ADR mới thay thế quyết định đặt Spring dưới `backend/`. Chưa triển khai WEB-01 hoặc Payment trong task đổi layout.

## 6. Nghiệm thu

- Không mất/đổi nội dung source, migration, test và Maven config khi di chuyển.
- Maven wrapper từ gốc nhận đúng `ci/settings.xml`; package/test/Spotless đạt theo phạm vi ghi trong bằng chứng thực tế.
- Compose local, lab và production validate; context local/lab resolve về gốc repo và UI độc lập.
- Workflow không còn cache, working directory hoặc build context trỏ vào `backend/` đã bỏ.
- `git diff --check` đạt; build UI không bị ảnh hưởng; không thay dữ liệu/secret/runtime đang dùng.

## 7. Kiểm chứng

Tại `D:/shop`, dùng Maven wrapper kiểm tra layout mới; kiểm tra Compose bằng file `.env.*.example`, không xuất cấu hình đã nội suy secret thật. Đối chiếu hash file và diff dạng rename. Kiểm tra workflow YAML/script khi có công cụ phù hợp. Chỉ báo cáo các gate thật sự đã chạy, không suy diễn CI xanh hoặc coverage mới.

## 8. Triển khai và rollback

Máy clone phiên bản mới chạy Maven/Docker build ngay tại `shop/`; image production dựng sẵn và `deploy/deploy.sh` giữ nguyên contract. Rollback layout bằng cách chuyển riêng các mục đã di chuyển về một thư mục `backend/` chưa tồn tại, rồi đảo các chỉnh sửa đường dẫn tương ứng. Không xóa/di chuyển `target`, `data`, `.git`, secret hoặc toàn bộ workspace; không dùng reset worktree.

## Bằng chứng local — 16/10/2026

- Chuyển đủ 557 file trong 8 mục trực tiếp về gốc repo; SHA-256 của cả 557 file khớp ngay sau chuyển. Sau đó chỉ mở rộng `.dockerignore` và sửa đường dẫn config/docs có chủ đích; không sửa Java, test, POM, migration hoặc nội dung Maven wrapper/config.
- `backend/` đã được gỡ khi trống. UI vẫn ở `D:/shop-ui`; không tạo bản source UI thứ hai hoặc thay đổi source giao diện.
- Compose local (có profile app), lab và production validate đạt. Local/lab backend context resolve `D:/shop`; UI lab context vẫn `D:/shop-ui`.
- Workflow CI/CD parse YAML đạt; `bash -n` đạt với Maven shell wrapper, `deploy/deploy.sh` và script init tài khoản MySQL. Chưa chạy GitHub Actions hoặc Docker image build mới.
- Maven wrapper chạy tại gốc, dùng `JAVA_HOME` chỉ trong phiên lệnh trỏ JDK 21.0.12.1. Lượt `verify` chạy đủ **400 test, 0 failure/error/skipped**, gồm Modulith/ArchUnit và integration trên MySQL/MinIO Testcontainers, sau đó dừng ở repackage vì JAR đang bị hai app local giữ trên Windows.
- Dừng đúng hai app local do lượt làm việc trước khởi tạo; chạy `-DskipTests verify` để hoàn tất package/Spotless/JaCoCo, dùng lại dữ liệu coverage của 400 test vừa chạy, đạt **BUILD SUCCESS**. Đây không phải lần chạy lại 400 test thứ hai. Khởi động lại app chính và QA từ JAR mới; không đổi schema hay credential/database người dùng.
- JaCoCo: instruction **90,69%**, line **90,55%**, branch **67,80%**; đạt gate hiện có nhưng **chưa đạt mục tiêu cuối dự án branch trên 80%**. Không sửa threshold hoặc loại mã khỏi báo cáo.
- JAR mới có Spring Boot launcher và migration MySQL V24. Health 8080/8081 trả UP; UI proxy 5173 vẫn trả catalog rỗng hiện có, 5174 trả 8 sản phẩm QA.
- Flyway có SQL chung tại `db/migration` và SQL theo dialect tại `db/mysql`/`db/h2`; cả source và schema local đều tới V24. Kiểm tra chỉ thư mục chung sẽ bỏ sót V11/V21/V24; không có migration bị mất trong lần chuyển layout.
- Build UI (TypeScript + Vite, 106 modules) và `git diff --check` đạt. File skill người dùng đã stage từ trước vẫn được giữ nguyên; không commit/push/merge, không thay remote hoặc triển khai cloud.

Log kiểm chứng nằm ngoài repo tại thư mục artifact local của lượt làm việc:
`C:/Users/minhp/.codex/visualizations/2026/10/06/01a10eda-4440-7702-9f71-ba84dd0e31c5/`,
gồm `root-layout-maven-20261016.log` (400 test + lỗi khóa JAR) và
`root-layout-package-20261016.log` (hoàn tất verify sau khi dừng app).

ROOT-01 đã có bằng chứng nghiệm thu local; chưa có CI/merge/deploy. Nhiệm vụ feature được đề xuất tiếp theo là WEB-01 theo [kế hoạch website](website-delivery-plan.md), chưa tự bắt đầu task thứ hai trong lượt đổi layout này.
