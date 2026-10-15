# syntax=docker/dockerfile:1.7
# ---- Build SPA ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
# VITE_API_BASE_URL mặc định "/api" (cùng origin qua edge nginx) — không cần
# nhúng URL tuyệt đối vào bundle.
RUN npm run build

# ---- Edge nginx phục vụ SPA + proxy API ----
FROM nginx:1.27-alpine AS runtime
RUN apk add --no-cache gettext   # envsubst

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/hardened.conf.template /etc/nginx/templates/hardened.conf.template
COPY nginx/baseline.conf.template /etc/nginx/templates/baseline.conf.template
COPY nginx/common-proxy.conf /etc/nginx/snippets/common-proxy.conf
COPY nginx/docker-entrypoint.sh /docker-entrypoint-custom.sh
RUN chmod +x /docker-entrypoint-custom.sh \
    && rm -f /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1/healthz >/dev/null 2>&1 || exit 1
ENTRYPOINT ["/docker-entrypoint-custom.sh"]
