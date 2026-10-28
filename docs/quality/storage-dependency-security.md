# SEC-DEP-01 — Vá dependency bắc cầu của object storage

## Hợp đồng tám phần — 28/10/2026

1. **Mục tiêu:** mở chặn gate phát hành khi Trivy phát hiện dependency có bản
   vá trong cây hiện tại. Tách khỏi PR #54 dọn deployment; không bỏ qua scanner.
2. **Sở hữu/phụ thuộc:** `pom.xml` quản lý phiên bản Guava và Snappy Java do
   `io.minio:minio:9.0.3` kéo vào. Java/API/schema và MinIO adapter không đổi.
3. **Bất biến:** Maven chỉ resolve một phiên bản đã vá cho mỗi thư viện; giữ
   contract upload/download, JWT, checkout, IPN và transaction hiện hành.
4. **An toàn:** không thêm ignore CVE, hạ severity, bỏ required check hoặc sửa
   IAM/secret. Có thư viện bị ảnh hưởng trong graph chưa chứng minh input HTTP
   của Shop đi tới đường khai thác đó; vẫn vá để loại dependency có cảnh báo.
5. **Đầu ra:** quản lý rõ `guava:33.7.2-jre` và `snappy-java:1.1.10.12`, tài
   liệu nguồn bản vá, bằng chứng dependency tree và PR độc lập.
6. **Ca kiểm:** Maven resolve/build, full regression MySQL/MinIO, kiểm module,
   migration, coverage và vulnerability scan theo gate CI hiện hữu.
7. **Nghiệm thu:** PR có `verify` và `dependency-review` xanh trên đúng HEAD;
   CI main xanh sau merge. Không lấy build `skipTests` thay cho regression.
8. **Phát hành/rollback:** phát hành qua PR bảo vệ main; CD tuân theo cờ hiện
   hữu và kiểm fleet. Revert version sẽ đưa cảnh báo trở lại nên ưu tiên
   roll-forward nếu có lỗi tương thích; không thay schema hoặc dữ liệu.

## Finding mở chặn

[CI của PR #54](https://github.com/triramisu/shop/actions/runs/40614488027)
đọc `pom.xml` với Trivy 0.75.0 và database tại thời điểm chạy. Kết quả:

| Dependency trước vá | CVE | Mức theo Trivy |
| --- | --- | --- |
| Guava 33.6.0-jre | CVE-2026-102554 | High |
| Snappy Java 1.1.10.8 | CVE-2026-108106 | High |
| Snappy Java 1.1.10.8 | CVE-2026-93451 | High |
| Snappy Java 1.1.10.8 | CVE-2026-116590 | Medium |

`dependency:tree` xác nhận cả hai đi qua MinIO, không phải dependency mới do
việc bỏ deployment lab. Required check chặn merge là hành vi đúng.

## Nguồn phiên bản

- [Guava 33.7.2](https://github.com/google/guava/releases/tag/v33.7.2) công bố
  Maven coordinate `33.7.2-jre`; [advisory của Google](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94)
  ghi bản vá cho eager allocation khi deserialize collection.
- [Snappy 1.1.10.9](https://github.com/xerial/snappy-java/releases/tag/v1.1.10.9)
  sửa buffer overflow/unbounded allocation; [advisory kiểm tra range](https://github.com/xerial/snappy-java/security/advisories/GHSA-xqrx-qh46-34m7)
  cũng xác nhận bản vá từ 1.1.10.9.
- Chọn [maintenance release 1.1.10.12](https://github.com/xerial/snappy-java/releases/tag/v1.1.10.12),
  đã xác minh có trên Maven Central, để lấy các sửa lỗi tiếp theo trong cùng
  dòng 1.1.10. Giữ MinIO hiện hành và quản lý override tập trung trong POM.

## Kiểm lại

Từ root repo với JDK 21:

```powershell
./mvnw.cmd --batch-mode dependency:tree '-Dincludes=com.google.guava:guava,org.xerial.snappy:snappy-java'
./mvnw.cmd --batch-mode clean verify
```

Regression cần Docker cho MySQL/MinIO. Khi Engine local tắt, CI runner vẫn
phải chạy đầy đủ; không loại test container để làm xanh. Bằng chứng CI/merge
được đối chiếu đúng SHA, không suy ra production đã nhận JAR chỉ từ merge.

## Bằng chứng local

Ngày 28/10/2026, JDK 21.0.12.1: `package -DskipTests`, Spotless và dependency
tree đạt. Tree resolve Guava 33.7.2-jre và Snappy Java 1.1.10.12 qua MinIO
9.0.3; không đổi API hoặc migration. Đây là kiểm build/resolve, không phải
full regression: test bị bỏ qua có chủ đích trong riêng lệnh package này;
required `verify` trên CI vẫn phải chạy đầy đủ với MySQL và MinIO.
