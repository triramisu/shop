# Lab chống chịu tải / DDoS — Shop target

Thư mục này dựng **Shop** thành một *target* để nghiên cứu khả năng chống chịu
tải và DDoS trong **mạng lab cô lập, đã được phép**. Mục tiêu là *đo và quan
sát* hành vi hệ thống dưới tải và hiệu quả của các lớp giảm thiểu — không phải
để tấn công bất kỳ ai.

## Ranh giới sử dụng (đọc trước)

- Chỉ chạy trên mạng **cô lập** (lab riêng, VLAN/subnet nội bộ, hoặc máy ảo
  không định tuyến ra ngoài). **Không** expose target ra Internet công cộng.
- Chỉ sinh tải khi bạn **sở hữu hoặc được phép** thử nghiệm trên target này, và
  tải chỉ nhắm vào **chính target này**, không nhắm bên thứ ba.
- Repo này **không kèm công cụ sinh tải/tấn công**. Để tạo tải hợp pháp trong
  lab, dùng các công cụ benchmark tiêu chuẩn bạn tự vận hành (ví dụ `k6`, `wrk`,
  `hey`, `vegeta`) nhắm vào `http://<target>:<port>`.
- Mọi lớp phòng vệ của ứng dụng (rate limit tầng JVM, CAPTCHA thích ứng) được
  **giữ bật** theo mặc định. Việc hạ thấp chúng chỉ nên làm có chủ đích để đo
  đối chứng, trong lab.

## Thành phần

```
[ công cụ tải của bạn ] → web (nginx edge) → app (Spring) → mysql
                                           ↘ minio
```

- `web` — nginx vừa phục vụ SPA React vừa là **edge** proxy `/api`. Đây là nơi
  đặt các lớp giảm thiểu để đo (`limit_req`, `limit_conn`, timeout).
- `app` — backend Spring (image build từ `Dockerfile` gốc), chạy read-only,
  `cap_drop: ALL`, `no-new-privileges`.
- `mysql`, `minio` — phụ trợ, chỉ bind loopback của host.

Mỗi container có **giới hạn CPU/RAM/pids** để lab có biên rõ ràng, host không bị
chiếm dụng hoàn toàn và kết quả đo lặp lại được.

## Khởi động

```bash
cp deploy/lab/.env.lab.example deploy/lab/.env.lab
# điền secret ngẫu nhiên trong .env.lab

# Trên máy Ubuntu lab (cài Docker + dựng stack, tùy chọn giới hạn firewall theo CIDR):
sudo deploy/lab/setup-ubuntu.sh --lab-cidr 10.10.0.0/24

# Hoặc thủ công nếu đã có Docker:
docker compose --env-file deploy/lab/.env.lab -f deploy/lab/compose.lab.yml up -d --build
```

Target phục vụ tại `http://<host-lab>:8080` (đổi bằng `WEB_PORT`).

## Đo đối chứng "có vs. không" giảm thiểu ở edge

Biến `EDGE_PROFILE` chọn cấu hình nginx edge:

| Profile    | Hành vi edge                                              |
|------------|-----------------------------------------------------------|
| `hardened` | Bật `limit_req` (API + auth), `limit_conn`, các timeout.  |
| `baseline` | Không giảm thiểu ở edge (chỉ còn phòng vệ tầng ứng dụng). |

Các tham số tinh chỉnh qua env: `EDGE_API_RATE`, `EDGE_API_BURST`,
`EDGE_AUTH_RATE`, `EDGE_AUTH_BURST`, `EDGE_CONN_PER_IP`, `EDGE_MAX_BODY`.

Đổi profile rồi dựng lại dịch vụ `web`:

```bash
# Mốc đối chứng không giảm thiểu
EDGE_PROFILE=baseline docker compose --env-file deploy/lab/.env.lab \
  -f deploy/lab/compose.lab.yml up -d web

# Bật lại giảm thiểu
EDGE_PROFILE=hardened docker compose --env-file deploy/lab/.env.lab \
  -f deploy/lab/compose.lab.yml up -d web
```

Chạy cùng một kịch bản tải cho mỗi profile và so sánh để thấy tác dụng của từng
lớp giảm thiểu.

## Quan sát

- Tài nguyên container theo thời gian thực: `docker stats`.
- Log edge (mã 429 khi rate limit chặn): `docker compose -f deploy/lab/compose.lab.yml logs -f web`.
- Sức khỏe app: `curl http://<host-lab>:8080/actuator/health`.
- Đo phía client bằng chính công cụ benchmark của bạn (p95/p99 latency, tỷ lệ
  lỗi, throughput) ở từng profile.

## Dọn dẹp

```bash
docker compose --env-file deploy/lab/.env.lab -f deploy/lab/compose.lab.yml down -v
```
