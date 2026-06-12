#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${ENV_FILE:-$PROJECT_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "No existe el fichero de entorno: $ENV_FILE" >&2
  exit 1
fi

read_env_value() {
  local key=$1
  local line value
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

BACKUP_DIR="${BACKUP_DIR:-$(read_env_value BACKUP_DIR)}"
BACKUP_DIR="$(readlink -m "${BACKUP_DIR:-/var/backups/gestor_documental}")"
BACKUP_MAX_AGE_HOURS="${BACKUP_MAX_AGE_HOURS:-$(read_env_value BACKUP_MAX_AGE_HOURS)}"
BACKUP_MAX_AGE_HOURS="${BACKUP_MAX_AGE_HOURS:-36}"
DISK_USAGE_WARN_PERCENT="${DISK_USAGE_WARN_PERCENT:-$(read_env_value DISK_USAGE_WARN_PERCENT)}"
DISK_USAGE_WARN_PERCENT="${DISK_USAGE_WARN_PERCENT:-85}"
MONITOR_SUCCESS_URL="${MONITOR_SUCCESS_URL:-$(read_env_value MONITOR_SUCCESS_URL)}"
MONITOR_FAILURE_URL="${MONITOR_FAILURE_URL:-$(read_env_value MONITOR_FAILURE_URL)}"
COMPOSE=(docker compose --project-directory "$PROJECT_DIR" --env-file "$ENV_FILE")
ERRORS=()

add_error() {
  ERRORS+=("$1")
}

for service in mysql app; do
  if [[ "$("${COMPOSE[@]}" ps --status running -q "$service" | wc -l)" -lt 1 ]]; then
    add_error "El servicio $service no esta en ejecucion"
  fi
done

if ! curl --fail --silent --max-time 10 http://127.0.0.1:8080/actuator/health \
  | grep -q '"status":"UP"'; then
  add_error "El healthcheck de la aplicacion no responde UP"
fi

DISK_USAGE="$(df -P "$PROJECT_DIR" | awk 'NR==2 {gsub(/%/, "", $5); print $5}')"
if [[ "$DISK_USAGE" =~ ^[0-9]+$ && "$DISK_USAGE" -ge "$DISK_USAGE_WARN_PERCENT" ]]; then
  add_error "El disco esta al ${DISK_USAGE}%"
fi

LATEST_BACKUP=""
if [[ -d "$BACKUP_DIR" ]]; then
  LATEST_BACKUP="$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' | sort | tail -n 1)"
fi
if [[ -z "$LATEST_BACKUP" ]]; then
  add_error "No existe ninguna copia de seguridad"
else
  BACKUP_AGE_HOURS="$(( ($(date +%s) - $(stat -c %Y "$LATEST_BACKUP")) / 3600 ))"
  if [[ "$BACKUP_AGE_HOURS" -gt "$BACKUP_MAX_AGE_HOURS" ]]; then
    add_error "La ultima copia tiene ${BACKUP_AGE_HOURS} horas"
  fi
  if ! (cd "$LATEST_BACKUP" && sha256sum -c SHA256SUMS >/dev/null 2>&1); then
    add_error "La ultima copia no supera la comprobacion de integridad"
  fi
fi

if "${COMPOSE[@]}" logs --since 10m app 2>&1 \
  | grep -Eq 'OutOfMemoryError|Application run failed|CannotGetJdbcConnectionException|HikariPool.*Exception'; then
  add_error "Se han detectado errores criticos recientes en el backend"
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  MESSAGE="Gestor documental: ${ERRORS[*]}"
  logger -t gestor-monitor "$MESSAGE" || true
  echo "$MESSAGE" >&2
  if [[ -n "${MONITOR_FAILURE_URL:-}" ]]; then
    curl --fail --silent --show-error --max-time 15 "$MONITOR_FAILURE_URL" >/dev/null || true
  fi
  exit 1
fi

MESSAGE="Gestor documental: aplicacion, disco y copia de seguridad correctos"
logger -t gestor-monitor "$MESSAGE" || true
echo "$MESSAGE"
if [[ -n "${MONITOR_SUCCESS_URL:-}" ]]; then
  curl --fail --silent --show-error --max-time 15 "$MONITOR_SUCCESS_URL" >/dev/null
fi
