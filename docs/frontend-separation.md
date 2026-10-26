# UI-STRUCTURE — Tách frontend ra thư mục riêng

[Mục lục tài liệu](README.md) · [Triển khai](../deploy/README.md)

Đây là biên bản task tách UI ngày 15/10/2026. Vị trí backend `shop/backend` và context backend trong biên bản này đã được thay thế bởi [ROOT-01](backend-root-layout.md): backend hiện ở thẳng `D:/shop`. Kết quả kiểm tra lịch sử bên dưới không được dùng như bằng chứng cho layout mới.

## 1. Mục tiêu, actor và phạm vi

Theo yêu cầu của chủ dự án, chuyển nguyên storefront từ `D:/shop/frontend` sang `D:/shop-ui`. Backend vẫn ở `D:/shop/backend`. Giữ nguyên giao diện, tài nguyên, cấu hình, package lock và luồng API. Tạm dừng WEB-01; không sửa nghiệp vụ, schema, payment hoặc hạ tầng AWS trong task này.

## 2. Sở hữu và phụ thuộc

`shop-ui` sở hữu React/TypeScript, Vite, nginx và Dockerfile UI; `shop` sở hữu Spring, migration và cấu hình triển khai backend. Hai bên giao tiếp qua `/api`, không import source xuyên thư mục. Node/npm và Docker là phụ thuộc build hiện có. Lab Compose cần cả hai thư mục, mặc định đặt cạnh nhau.

## 3. Luồng và bất biến

Kiểm tra thư mục đích trống trước khi chuyển. Di chuyển cả file ẩn, source, ảnh/font, lockfile và thư mục runtime hiện có; không tạo bản UI thứ hai để phát triển song song. Dev vẫn dùng Vite proxy tới backend, production nginx vẫn proxy `/api` tới `APP_UPSTREAM`. Tên image và hợp đồng API giữ nguyên.

## 4. Bảo mật và vận hành

Không đọc/in secret môi trường, không nhúng credential vào `VITE_*`, không nới CORS. Giữ ignore cho `.env*`, `node_modules`, `dist`; thêm quy tắc LF cho shell entrypoint khi UI không còn kế thừa Git attributes của backend. Không tạo remote, push, deploy hay thay đổi database. Lịch sử UI cũ vẫn còn trong Git của Shop; repo UI local mới chưa có remote/commit.

## 5. Đầu ra

Frontend độc lập tại `D:/shop-ui`; README hai phía, quyết định kiến trúc và kế hoạch website phản ánh layout mới. Lab Compose dùng `SHOP_UI_CONTEXT` với mặc định `../../../shop-ui` tính từ file Compose, hỗ trợ đường dẫn tùy chỉnh. CI/CD backend hiện có vẫn build `backend/`; CI/CD UI là nhiệm vụ riêng.

## 6. Trường hợp nghiệm thu

- File đã có trước khi chuyển còn đủ và giữ nguyên nội dung, trừ README được sửa có chủ đích.
- Đích có Dockerfile, nginx, `.env.example`, package lock, ảnh/font và source; không còn source UI hoạt động trong `shop/frontend`.
- TypeScript và Vite build từ thư mục mới thành công.
- Compose resolve đúng context backend/UI với mặc định và khi override; không dựng hay thay thế stack đang chạy.
- Dev server khởi động từ thư mục mới và phục vụ SPA; kiểm tra API proxy nếu backend local sẵn sàng.

## 7. Lệnh kiểm chứng

Từ `shop-ui`: `npm run build`. Từ `shop`: `git diff --check` và `docker compose --env-file deploy/lab/.env.lab.example -f deploy/lab/compose.lab.yml config --quiet`. So sánh hash file trước/sau di chuyển, kiểm tra context đã resolve và smoke HTTP. Không coi các kiểm tra này là CI xanh, payment sandbox hoặc full regression backend.

## 8. Tương thích và rollback

Đây là thay đổi layout local/build, không đổi API hay schema. Khi build lab từ source, đặt hai project cạnh nhau hoặc cung cấp `SHOP_UI_CONTEXT`; triển khai backend bằng image dựng sẵn không bị ảnh hưởng. Rollback: dừng dev server liên quan, bảo đảm `shop/frontend` chưa tồn tại, chuyển source/config/tài nguyên UI về đó bằng thao tác file an toàn và khôi phục riêng đường dẫn Compose/docs tương ứng. Giữ `.git` của UI riêng, không đưa nó vào repo backend. Không reset worktree, không xóa Git hoặc dữ liệu của người dùng.

## Bằng chứng kiểm tra local — 15/10/2026

- Ghi nhận SHA-256 của 63 file nguồn/config/tài nguyên trước khi chuyển; cả 63 file khớp ngay sau khi chuyển. Sau đó chỉ sửa README/ignore có chủ đích và thêm `.gitattributes`; không sửa code ứng dụng.
- `npm run build` tại `D:/shop-ui` đạt cả hai TypeScript gate và Vite build (106 modules).
- Compose lab validate thành công bằng cấu hình mẫu; context resolve thành `D:/shop/backend` và `D:/shop-ui`. Override `SHOP_UI_CONTEXT=D:/shop-ui` cũng resolve đúng. Compose backend-only vẫn validate thành công.
- Dev server chạy từ thư mục mới: cổng 5173 nối backend 8080, trả catalog rỗng hiện có; cổng 5174 nối backend QA 8081, trả đủ 8 sản phẩm QA. Trang chủ trả 200 ở cả hai cổng.
- Ảnh hero trả `image/png`, font nội bộ trả `font/ttf`; API giỏ hàng khi chưa đăng nhập vẫn trả 401, không bị SPA fallback.
- Ignore hoạt động cho `.env.local`, `node_modules/`, `dist/`; `.env.example` không bị ignore. Shell entrypoint giữ LF. `git diff --check` của backend và kiểm tra whitespace README UI đạt.
- Thay đổi đã stage trước đó trong `.agent/skills/shop-roadmap/SKILL.md` được giữ nguyên. Không sửa Java, migration, backend CI/CD, Compose backend hoặc dữ liệu SQL/MinIO.

Chưa commit/push, tạo remote UI, build image Docker mới, chạy CI hay deploy. Không chạy lại full Maven/coverage vì task này không thay đổi Java/schema; các kết quả trên không chứng minh gate production đã đạt. WEB-01 vẫn tạm dừng.
