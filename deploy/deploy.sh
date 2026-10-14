#!/usr/bin/env bash
set -Eeuo pipefail

readonly DEPLOY_ROOT="${SHOP_DEPLOY_ROOT:-/opt/shop}"
readonly COMPOSE_FILE="${DEPLOY_ROOT}/compose.prod.yml"
readonly ENV_FILE="${DEPLOY_ROOT}/.env.prod"
readonly HEALTH_URL="${SHOP_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
readonly EXPECTED_IMAGE_REPOSITORY="docker.io/t1r4m15u/shop"
readonly HEALTH_ATTEMPTS="${SHOP_HEALTH_ATTEMPTS:-30}"
readonly HEALTH_DELAY_SECONDS="${SHOP_HEALTH_DELAY_SECONDS:-3}"

new_image="${1:-}"

is_allowed_image() {
  local image="$1"
  [[ "$image" =~ ^docker\.io/t1r4m15u/shop:sha-[0-9a-f]{40}$ ]] \
    || [[ "$image" =~ ^docker\.io/t1r4m15u/shop@sha256:[0-9a-f]{64}$ ]]
}

if ! is_allowed_image "$new_image"; then
  echo "Image must match ${EXPECTED_IMAGE_REPOSITORY}:sha-<commit> or ${EXPECTED_IMAGE_REPOSITORY}@sha256:<digest>" >&2
  exit 2
fi

for command_name in docker curl flock grep; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command ${command_name} is not installed" >&2
    exit 2
  fi
done

if [[ ! "$HEALTH_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] \
  || [[ ! "$HEALTH_DELAY_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "Health-check attempts and delay must be positive integers" >&2
  exit 2
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is not available" >&2
  exit 2
fi

if [[ ! -f "$COMPOSE_FILE" || ! -f "$ENV_FILE" ]]; then
  echo "${COMPOSE_FILE} and ${ENV_FILE} must exist" >&2
  exit 2
fi

exec 9>"${DEPLOY_ROOT}/.deploy.lock"
if ! flock -n 9; then
  echo "Another Shop deployment is already running" >&2
  exit 3
fi

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
app_container_id="$("${compose[@]}" ps -q app 2>/dev/null || true)"
previous_image="$(docker inspect --format '{{.Config.Image}}' "$app_container_id" 2>/dev/null || true)"

wait_until_healthy() {
  local attempt
  for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt++)); do
    if curl --fail --silent --show-error --max-time 3 "$HEALTH_URL" \
      | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      return 0
    fi
    sleep "$HEALTH_DELAY_SECONDS"
  done
  return 1
}

deploy_image() {
  local image="$1"
  export SHOP_IMAGE="$image"
  "${compose[@]}" pull app
  "${compose[@]}" up -d mysql minio
  "${compose[@]}" up -d --no-deps app
}

echo "Deploying ${new_image}"
deploy_image "$new_image"

if wait_until_healthy; then
  echo "Deployment is healthy: ${new_image}"
  exit 0
fi

echo "Health check failed for ${new_image}" >&2
export SHOP_IMAGE="$new_image"
"${compose[@]}" logs --tail=100 app >&2 || true

if is_allowed_image "$previous_image" && [[ "$previous_image" != "$new_image" ]]; then
  echo "Rolling back to ${previous_image}" >&2
  deploy_image "$previous_image"
  if wait_until_healthy; then
    echo "Rollback is healthy: ${previous_image}" >&2
  else
    echo "Rollback also failed; manual recovery is required" >&2
  fi
else
  echo "No eligible previous image is available; stopping the failed app" >&2
  "${compose[@]}" stop app || true
fi

exit 1
