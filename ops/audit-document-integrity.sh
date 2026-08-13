#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${ENV_FILE:-$PROJECT_DIR/.env}"
REPORT_PATH="${1:-}"
AUDIT_MAX_DISPLAY="${AUDIT_MAX_DISPLAY:-100}"
COMPOSE=(docker compose --project-directory "$PROJECT_DIR" --env-file "$ENV_FILE")
TEMP_DIR="$(mktemp -d)"
DOCUMENTS_FILE="$TEMP_DIR/documents.tsv"
PAGES_FILE="$TEMP_DIR/pages.tsv"
REPORT_FILE="$TEMP_DIR/report.tsv"

cleanup() {
  rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT

if [[ ! -f "$ENV_FILE" ]]; then
  echo "No existe el fichero de entorno: $ENV_FILE" >&2
  exit 1
fi
if [[ ! "$AUDIT_MAX_DISPLAY" =~ ^[0-9]+$ ]]; then
  echo "AUDIT_MAX_DISPLAY debe ser un numero entero." >&2
  exit 1
fi
for service in mysql app; do
  if [[ "$("${COMPOSE[@]}" ps --status running -q "$service" | wc -l)" -lt 1 ]]; then
    echo "El servicio $service no esta en ejecucion." >&2
    exit 1
  fi
done

"${COMPOSE[@]}" exec -T mysql sh -c \
  'exec mysql --batch --raw --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > "$DOCUMENTS_FILE" <<'SQL'
SELECT d.id,
       COALESCE(d.expediente_id, ''),
       COALESCE(e.matricula, ''),
       d.tipo_documento,
       COALESCE(d.nombre_archivo, ''),
       COALESCE(d.expediente_completo_origen_id, ''),
       COALESCE(d.paginas_expediente_completo, ''),
       d.fecha_subida
FROM documento d
LEFT JOIN expediente e ON e.id = d.expediente_id
ORDER BY d.id;
SQL

cut -f1,5 "$DOCUMENTS_FILE" | "${COMPOSE[@]}" exec -T app sh -c '
while IFS="$(printf "\t")" read -r id filename; do
  if [ -z "$filename" ]; then
    printf "%s\tSIN_NOMBRE\t\n" "$id"
    continue
  fi
  path="/app/uploads/$filename"
  if [ ! -f "$path" ]; then
    printf "%s\tAUSENTE\t\n" "$id"
    continue
  fi
  lower="$(printf "%s" "$filename" | tr "[:upper:]" "[:lower:]")"
  case "$lower" in
    *.jpg|*.jpeg|*.png|*.webp)
      printf "%s\tOK\t1\n" "$id"
      ;;
    *.pdf)
      pages="$(pdfinfo "$path" 2>/dev/null | awk "/^Pages:/ {print \$2; exit}")"
      if [ -n "$pages" ]; then
        printf "%s\tOK\t%s\n" "$id" "$pages"
      else
        printf "%s\tPDF_INVALIDO\t\n" "$id"
      fi
      ;;
    *)
      printf "%s\tFORMATO_NO_VERIFICABLE\t\n" "$id"
      ;;
  esac
done
' > "$PAGES_FILE"

printf 'TIPO\tDOCUMENTO_ID\tEXPEDIENTE\tDETALLE\n' > "$REPORT_FILE"

awk -F '\t' -v OFS='\t' '
FNR == NR {
  file_status[$1] = $2
  page_count[$1] = $3
  next
}
{
  id = $1
  ids[++total] = id
  expediente[id] = $2
  matricula[id] = $3
  tipo[id] = $4
  filename[id] = $5
  parent[id] = $6
  mapping[id] = $7
  fecha[id] = $8
  if ($6 != "") {
    managed_exp[$2] = 1
    child_count[$6]++
    if (!($2 in managed_since) || $8 < managed_since[$2]) {
      managed_since[$2] = $8
    }
  }

}
function issue(code, id, exp_id, detail) {
  label = matricula[id] != "" ? matricula[id] : exp_id
  print code, id, label, detail
}
END {

  for (i = 1; i <= total; i++) {
    id = ids[i]
    exp_id = expediente[id]
    status = file_status[id]
    if (status != "OK" && status != "FORMATO_NO_VERIFICABLE") {
      issue("FICHERO_" status, id, exp_id, filename[id])
    }
    if (tipo[id] != "EXPEDIENTE_COMPLETO" && exp_id != "" && parent[id] == "" && managed_exp[exp_id] && fecha[id] >= managed_since[exp_id]) {
      issue("SIN_VINCULO_COMPLETO", id, exp_id, tipo[id] " | " filename[id])
    }
    if (parent[id] == "") {
      continue
    }
    p = parent[id]
    if (p == id) {
      issue("AUTORREFERENCIA_COMPLETO", id, exp_id, "expediente_completo_origen_id=" p)
      continue
    }
    if (!(p in tipo)) {
      issue("PADRE_INEXISTENTE", id, exp_id, "expediente_completo_origen_id=" p)
      continue
    }
    if (tipo[p] != "EXPEDIENTE_COMPLETO") {
      issue("PADRE_NO_ES_COMPLETO", id, exp_id, "padre=" p " tipo=" tipo[p])
    }
    if (expediente[p] != exp_id) {
      issue("PADRE_OTRO_EXPEDIENTE", id, exp_id, "padre=" p)
    }
    if (mapping[id] == "") {
      issue("SIN_RANGO_PAGINAS", id, exp_id, "padre=" p)
      continue
    }
    mapped = split(mapping[id], values, ",")
    if (file_status[id] == "OK" && page_count[id] != "" && mapped != page_count[id]) {
      issue("NUMERO_PAGINAS_INCOHERENTE", id, exp_id,
            "fichero=" page_count[id] " rango=" mapping[id])
    }
    for (j = 1; j <= mapped; j++) {
      page = values[j]
      if (page !~ /^[0-9]+$/) {
        issue("RANGO_INVALIDO", id, exp_id, mapping[id])
        continue
      }
      key = p SUBSEP page
      if (key in used_by) {
        issue("PAGINA_SOLAPADA", id, exp_id,
              "padre=" p " pagina=" page " tambien_documento=" used_by[key])
      } else {
        used_by[key] = id
      }
      if (!(p in min_used) || page < min_used[p]) {
        min_used[p] = page
      }
      if (file_status[p] == "OK" && page_count[p] != "" && page >= page_count[p]) {
        issue("PAGINA_FUERA_DE_RANGO", id, exp_id,
              "padre=" p " pagina=" page " total=" page_count[p])
      }
    }
  }
  for (i = 1; i <= total; i++) {
    id = ids[i]
    if (tipo[id] != "EXPEDIENTE_COMPLETO" || file_status[id] != "OK" || page_count[id] == "" || child_count[id] == 0) {
      continue
    }
    if (!(id in min_used)) {
      continue
    }
    for (page = min_used[id]; page < page_count[id]; page++) {
      key = id SUBSEP page
      if (!(key in used_by)) {
        issue("PAGINA_SIN_MAPEAR", id, expediente[id], "pagina=" page)
      }
    }
  }

}
' "$PAGES_FILE" "$DOCUMENTS_FILE" >> "$REPORT_FILE"

"${COMPOSE[@]}" exec -T mysql sh -c \
  'exec mysql --batch --raw --skip-column-names -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  >> "$REPORT_FILE" <<'SQL'
SELECT 'LECTURA_IDENTIDAD_SIN_DOCUMENTO', l.documento_id, '', CONCAT('lectura_id=', l.id)
FROM documento_identidad_lectura l
LEFT JOIN documento d ON d.id = l.documento_id
WHERE d.id IS NULL
UNION ALL
SELECT 'LECTURA_ROLES_SIN_DOCUMENTO', l.documento_id, '', CONCAT('lectura_id=', l.id)
FROM documento_roles_lectura l
LEFT JOIN documento d ON d.id = l.documento_id
WHERE d.id IS NULL
UNION ALL
SELECT 'LECTURA_VEHICULO_SIN_DOCUMENTO', l.documento_id, '', CONCAT('lectura_id=', l.id)
FROM documento_vehiculo_lectura l
LEFT JOIN documento d ON d.id = l.documento_id
WHERE d.id IS NULL
UNION ALL
SELECT 'ITEM_IA_SIN_DOCUMENTO', i.documento_id, '', CONCAT('item_id=', i.id)
FROM solicitud_lectura_ia_item i
LEFT JOIN documento d ON d.id = i.documento_id
WHERE d.id IS NULL;
SQL

ISSUE_COUNT="$(( $(wc -l < "$REPORT_FILE") - 1 ))"
if [[ -n "$REPORT_PATH" ]]; then
  install -m 600 "$REPORT_FILE" "$REPORT_PATH"
  echo "Informe completo: $REPORT_PATH"
fi

if [[ "$ISSUE_COUNT" -gt 0 ]]; then
  echo "Auditoria documental: $ISSUE_COUNT incidencia(s)."
  if [[ "$AUDIT_MAX_DISPLAY" -gt 0 ]]; then
    head -n "$((AUDIT_MAX_DISPLAY + 1))" "$REPORT_FILE"
  fi
  exit 2
fi

echo "Auditoria documental correcta: no se han detectado incoherencias."
