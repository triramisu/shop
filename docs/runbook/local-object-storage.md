# Object storage local

## Mục tiêu

Môi trường `dev` lưu binary ảnh sản phẩm trong object storage tương thích MinIO/S3. MySQL chỉ lưu metadata và object key; API trả presigned URL có thời hạn thay vì Base64.

MinIO Community Server không còn phát hành image chính thức. Docker Compose local dùng Silo, một fork tương thích API, biến môi trường và định dạng dữ liệu MinIO. Java adapter vẫn dùng MinIO SDK và không phụ thuộc vào API riêng của Silo.

## Khởi động

```powershell
docker compose up -d minio
docker compose ps minio
```

- S3 API: `http://localhost:9000`
- Console: `http://localhost:9001`
- Bucket mặc định: `shop-product-images`
- Volume: `shop-minio-data`

Tài khoản mặc định trong Compose chỉ dành cho máy local. Có thể ghi đè bằng `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_ACCESS_KEY` và `MINIO_SECRET_KEY`. Không dùng các giá trị local này ở production.

Ứng dụng profile `dev` tự tạo bucket khi chưa tồn tại. Bucket mới mặc định là private; chỉ presigned URL mới đọc được object. Có thể khởi tạo thủ công từ container:

```powershell
docker exec shop-minio mcli alias set local http://127.0.0.1:9000 <access-key> <secret-key>
docker exec shop-minio mcli mb --ignore-existing local/shop-product-images
```

## Cấu hình

| Biến | Mặc định local | Ý nghĩa |
|---|---|---|
| `OBJECT_STORAGE_TYPE` | `minio` | Chọn adapter object storage |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Endpoint S3/MinIO |
| `MINIO_ACCESS_KEY` | giá trị demo local | Access key của ứng dụng |
| `MINIO_SECRET_KEY` | giá trị demo local | Secret key của ứng dụng |
| `MINIO_BUCKET` | `shop-product-images` | Bucket private chứa ảnh |
| `MINIO_AUTO_CREATE_BUCKET` | `true` | Cho phép tạo bucket ở local |
| `MINIO_PRESIGNED_URL_TTL` | `15m` | Thời hạn URL tải ảnh, tối đa 7 ngày |

Profile `prod` bắt buộc truyền endpoint, access key, secret key và bucket; thiếu cấu hình làm ứng dụng fail-fast. `MINIO_AUTO_CREATE_BUCKET` mặc định là `false` ở production để bucket và policy được quản trị bởi hạ tầng.

## Kiểm tra nhanh

```powershell
docker inspect --format='{{.State.Health.Status}}' shop-minio
docker compose restart minio
docker exec shop-minio mcli ls --recursive local/shop-product-images
```

Sau restart, object vẫn phải tồn tại nhờ named volume. Không chạy `docker compose down -v` nếu cần giữ dữ liệu local vì tùy chọn `-v` xóa volume.
