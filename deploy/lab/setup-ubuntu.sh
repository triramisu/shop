#!/usr/bin/env bash
#
# Chuẩn bị một máy Ubuntu (20.04/22.04/24.04) làm TARGET cho lab nghiên cứu
# sức chịu tải / chống chịu DDoS. Script này CHỈ cài runtime và dựng stack;
# nó KHÔNG sinh tải và KHÔNG chứa công cụ tấn công.
#
# CẢNH BÁO: chỉ chạy trên máy trong mạng lab CÔ LẬP đã được phép. Không expose
# target ra Internet công cộng.
#
# Cách dùng:
#   sudo ./setup-ubuntu.sh [--lab-cidr 10.10.0.0/24]
#
set -Eeuo pipefail

LAB_CIDR=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --lab-cidr) LAB_CIDR="${2:-}"; shift 2 ;;
    *) echo "Tham so khong hop le: $1" >&2; exit 2 ;;
  esac
done

if [[ "${EUID}" -ne 0 ]]; then
  echo "Can chay bang sudo/root." >&2
  exit 1
fi

if ! grep -qi ubuntu /etc/os-release 2>/dev/null; then
  echo "Script nay danh cho Ubuntu." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env.lab"

echo "==> Cai dat Docker Engine + Compose plugin"
if ! command -v docker >/dev/null 2>&1; then
  apt-get update
  apt-get install -y ca-certificates curl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
else
  echo "Docker da co san: $(docker --version)"
fi

echo "==> Tang gioi han file descriptor (de do tai ben dong nhieu ket noi)"
cat >/etc/sysctl.d/99-shop-lab.conf <<'SYSCTL'
# Chi phuc vu quan sat hanh vi duoi tai trong lab. Dieu chinh theo nhu cau.
net.core.somaxconn = 1024
net.ipv4.tcp_max_syn_backlog = 2048
SYSCTL
sysctl --system >/dev/null || true

if [[ -n "${LAB_CIDR}" ]]; then
  echo "==> Cau hinh firewall: chi cho phep ${LAB_CIDR} truy cap be mat target"
  if command -v ufw >/dev/null 2>&1; then
    WEB_PORT="$(grep -E '^WEB_PORT=' "${ENV_FILE}" 2>/dev/null | cut -d= -f2)"
    WEB_PORT="${WEB_PORT:-8080}"
    ufw allow from "${LAB_CIDR}" to any port 22 proto tcp || true
    ufw allow from "${LAB_CIDR}" to any port "${WEB_PORT}" proto tcp || true
    ufw --force enable || true
    echo "Da gioi han truy cap port ${WEB_PORT} trong ${LAB_CIDR}."
  else
    echo "ufw chua co; bo qua buoc firewall. Hay tu han che mang lab." >&2
  fi
else
  echo "LUU Y: chua truyen --lab-cidr. Hay tu dam bao mang lab duoc cach ly." >&2
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Thieu ${ENV_FILE}. Sao chep tu .env.lab.example va dien secret truoc khi khoi dong." >&2
  exit 1
fi

echo "==> Build va khoi dong stack lab"
docker compose --env-file "${ENV_FILE}" -f "${SCRIPT_DIR}/compose.lab.yml" up -d --build

echo "==> Trang thai"
docker compose --env-file "${ENV_FILE}" -f "${SCRIPT_DIR}/compose.lab.yml" ps

echo "Hoan tat. Target lab phuc vu tai http://<host>:$(grep -E '^WEB_PORT=' "${ENV_FILE}" | cut -d= -f2 || echo 8080)"
echo "Quan sat: docker stats | docker compose -f compose.lab.yml logs -f web"
