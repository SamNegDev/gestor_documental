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
BACKUP_DIR="${BACKUP_DIR:-/var/backups/gestor_documental}"
BACKUP_DAILY_COPIES="${BACKUP_DAILY_COPIES:-$(read_env_value BACKUP_DAILY_COPIES)}"
BACKUP_DAILY_COPIES="${BACKUP_DAILY_COPIES:-7}"
BACKUP_WEEKLY_COPIES="${BACKUP_WEEKLY_COPIES:-$(read_env_value BACKUP_WEEKLY_COPIES)}"
BACKUP_WEEKLY_COPIES="${BACKUP_WEEKLY_COPIES:-4}"
BACKUP_RCLONE_REMOTE="${BACKUP_RCLONE_REMOTE:-$(read_env_value BACKUP_RCLONE_REMOTE)}"
MONITOR_FAILURE_URL="${MONITOR_FAILURE_URL:-$(read_env_value MONITOR_FAILURE_URL)}"
MYSQL_DATABASE_NAME="${MYSQL_DATABASE:-$(read_env_value MYSQL_DATABASE)}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_DIR="$(readlink -m "$BACKUP_DIR")"
FINAL_DIR="$BACKUP_DIR/$TIMESTAMP"
TEMP_DIR="$BACKUP_DIR/.${TIMESTAMP}.tmp"
COMPOSE=(docker compose --project-directory "$PROJECT_DIR" --env-file "$ENV_FILE")

notify_failure() {
  local exit_code=$?
  rm -rf -- "$TEMP_DIR"
  if [[ -n "${MONITOR_FAILURE_URL:-}" ]]; then
    curl --fail --silent --show-error --max-time 15 "$MONITOR_FAILURE_URL" >/dev/null || true
  fi
  echo "La copia de seguridad ha fallado." >&2
  exit "$exit_code"
}

trap notify_failure ERR

if [[ "$BACKUP_DIR" == "/" || ${#BACKUP_DIR} -lt 8 ]]; then
  echo "BACKUP_DIR no es una ruta segura: $BACKUP_DIR" >&2
  exit 1
fi
if [[ ! "$BACKUP_DAILY_COPIES" =~ ^[1-9][0-9]*$ ]]; then
  echo "BACKUP_DAILY_COPIES debe ser un entero mayor que cero." >&2
  exit 1
fi
if [[ ! "$BACKUP_WEEKLY_COPIES" =~ ^[0-9]+$ ]]; then
  echo "BACKUP_WEEKLY_COPIES debe ser un entero igual o mayor que cero." >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
rm -rf -- "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

if [[ "$("${COMPOSE[@]}" ps --status running -q mysql | wc -l)" -lt 1 ]]; then
  echo "El contenedor MySQL no esta en ejecucion." >&2
  exit 1
fi

echo "Creando copia de MySQL..."
"${COMPOSE[@]}" exec -T mysql sh -c \
  'exec mysqldump --single-transaction --quick --routines --triggers --events --databases "$MYSQL_DATABASE" -uroot -p"$MYSQL_ROOT_PASSWORD"' \
  | gzip -9 > "$TEMP_DIR/database.sql.gz"

echo "Creando copia de documentos..."
if [[ ! -d "$PROJECT_DIR/uploads" ]]; then
  echo "No existe el directorio de documentos: $PROJECT_DIR/uploads" >&2
  exit 1
fi
tar -czf "$TEMP_DIR/uploads.tar.gz" -C "$PROJECT_DIR" uploads

cat > "$TEMP_DIR/metadata.txt" <<EOF
created_at_utc=$TIMESTAMP
project_dir=$PROJECT_DIR
database=${MYSQL_DATABASE_NAME:-unknown}
compose_project=$PROJECT_DIR
EOF

(
  cd "$TEMP_DIR"
  sha256sum database.sql.gz uploads.tar.gz metadata.txt > SHA256SUMS
  gzip -t database.sql.gz
  tar -tzf uploads.tar.gz >/dev/null
)

mv "$TEMP_DIR" "$FINAL_DIR"

if [[ -n "$BACKUP_RCLONE_REMOTE" ]]; then
  if ! command -v rclone >/dev/null 2>&1; then
    echo "BACKUP_RCLONE_REMOTE esta configurado, pero rclone no esta instalado." >&2
    exit 1
  fi
  echo "Replicando copia en almacenamiento externo..."
  rclone copy "$FINAL_DIR" "${BACKUP_RCLONE_REMOTE%/}/$TIMESTAMP" --checksum
fi

mapfile -t backups < <(
  find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d \
    -name '20??????T??????Z' -printf '%f\n' | sort -r
)
declare -A conservar=()
declare -A fechas_diarias=()
diarias=0
for nombre in "${backups[@]}"; do
  fecha="${nombre:0:8}"
  if [[ -z "${fechas_diarias[$fecha]:-}" && "$diarias" -lt "$BACKUP_DAILY_COPIES" ]]; then
    conservar["$nombre"]=1
    fechas_diarias["$fecha"]=1
    diarias=$((diarias + 1))
  fi
done

fecha_diaria_mas_antigua="$(printf '%s\n' "${!fechas_diarias[@]}" | sort | head -n 1)"
declare -A semanas=()
semanales=0
if [[ "$BACKUP_WEEKLY_COPIES" -gt 0 ]]; then
  for nombre in "${backups[@]}"; do
    fecha="${nombre:0:8}"
    if [[ "$fecha" < "$fecha_diaria_mas_antigua" ]]; then
      semana="$(date -u -d "${fecha:0:4}-${fecha:4:2}-${fecha:6:2}" +%G-%V)"
      if [[ -z "${semanas[$semana]:-}" && "$semanales" -lt "$BACKUP_WEEKLY_COPIES" ]]; then
        conservar["$nombre"]=1
        semanas["$semana"]=1
        semanales=$((semanales + 1))
      fi
    fi
  done
fi

eliminadas=0
for nombre in "${backups[@]}"; do
  [[ -n "${conservar[$nombre]:-}" ]] && continue
  [[ "$nombre" =~ ^20[0-9]{6}T[0-9]{6}Z$ ]] || {
    echo "Nombre de copia no seguro: $nombre" >&2
    exit 1
  }
  objetivo="$(readlink -f "$BACKUP_DIR/$nombre")"
  [[ "$objetivo" == "$BACKUP_DIR/"* && "$objetivo" != "$BACKUP_DIR" ]] || {
    echo "Ruta de copia no segura: $objetivo" >&2
    exit 1
  }
  rm -rf -- "$objetivo"
  eliminadas=$((eliminadas + 1))
done

echo "Retencion aplicada: $diarias copias diarias, $semanales semanales y $eliminadas eliminadas."

trap - ERR
echo "Copia completada: $FINAL_DIR"
