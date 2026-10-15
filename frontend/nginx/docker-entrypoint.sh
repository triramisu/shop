#!/bin/sh
set -eu

# Chọn profile edge và render biến môi trường vào file cấu hình nginx.
# EDGE_PROFILE = hardened (mặc định) | baseline
EDGE_PROFILE="${EDGE_PROFILE:-hardened}"

# Giá trị mặc định cho các tham số giảm thiểu (có thể override qua env).
export APP_UPSTREAM="${APP_UPSTREAM:-app:8080}"
export EDGE_API_RATE="${EDGE_API_RATE:-50r/s}"
export EDGE_API_BURST="${EDGE_API_BURST:-100}"
export EDGE_AUTH_RATE="${EDGE_AUTH_RATE:-5r/s}"
export EDGE_AUTH_BURST="${EDGE_AUTH_BURST:-10}"
export EDGE_CONN_PER_IP="${EDGE_CONN_PER_IP:-20}"
export EDGE_MAX_BODY="${EDGE_MAX_BODY:-30m}"

TEMPLATE="/etc/nginx/templates/${EDGE_PROFILE}.conf.template"
if [ ! -f "$TEMPLATE" ]; then
  echo "Khong tim thay profile edge: ${EDGE_PROFILE}" >&2
  exit 1
fi

VARS='${APP_UPSTREAM} ${EDGE_API_RATE} ${EDGE_API_BURST} ${EDGE_AUTH_RATE} ${EDGE_AUTH_BURST} ${EDGE_CONN_PER_IP} ${EDGE_MAX_BODY}'
envsubst "$VARS" < "$TEMPLATE" > /etc/nginx/conf.d/default.conf

echo "Edge nginx khoi dong voi profile: ${EDGE_PROFILE} (upstream ${APP_UPSTREAM})"
nginx -t
exec nginx -g 'daemon off;'
