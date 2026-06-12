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
BACKUP_PATH="${1:-}"
MYSQL_VERIFY_IMAGE="${MYSQL_VERIFY_IMAGE:-$(read_env_value MYSQL_VERIFY_IMAGE)}"
MYSQL_VERIFY_IMAGE="${MYSQL_VERIFY_IMAGE:-mysql:8.4}"
CONTAINER_NAME="gestor_backup_verify_${RANDOM}_${RANDOM}"
VERIFY_PASSWORD="verify-${RANDOM}-${RANDOM}-${RANDOM}"

if [[ -z "$BACKUP_PATH" ]]; then
  BACKUP_PATH="$(find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20??????T??????Z' | sort | tail -n 1)"
fi

if [[ -z "$BACKUP_PATH" || ! -d "$BACKUP_PATH" ]]; then
  echo "No se ha encontrado una copia para verificar." >&2
  exit 1
fi

cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Validando archivos y sumas de comprobacion..."
(
  cd "$BACKUP_PATH"
  sha256sum -c SHA256SUMS
  gzip -t database.sql.gz
  tar -tzf uploads.tar.gz >/dev/null
)

echo "Arrancando MySQL temporal para probar la restauracion..."
docker run -d --rm \
  --name "$CONTAINER_NAME" \
  -e MYSQL_ROOT_PASSWORD="$VERIFY_PASSWORD" \
  "$MYSQL_VERIFY_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" sh -c \
    'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1"' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! docker exec "$CONTAINER_NAME" sh -c \
  'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT 1"' >/dev/null 2>&1; then
  echo "El MySQL temporal no ha arrancado a tiempo." >&2
  exit 1
fi

gzip -dc "$BACKUP_PATH/database.sql.gz" \
  | docker exec -i "$CONTAINER_NAME" sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

TABLE_COUNT="$(docker exec "$CONTAINER_NAME" sh -c \
  'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema NOT IN (\"information_schema\", \"mysql\", \"performance_schema\", \"sys\");"')"

if [[ ! "$TABLE_COUNT" =~ ^[0-9]+$ || "$TABLE_COUNT" -lt 1 ]]; then
  echo "La restauracion no ha generado tablas de aplicacion." >&2
  exit 1
fi

echo "Restauracion verificada correctamente: $TABLE_COUNT tablas recuperadas."
