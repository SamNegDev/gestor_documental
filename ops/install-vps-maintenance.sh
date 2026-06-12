#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Ejecuta este instalador con sudo." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

install_unit() {
  local name=$1
  local content=$2
  printf '%s\n' "$content" > "/etc/systemd/system/$name"
}

install_unit "gestor-backup.service" "[Unit]
Description=Copia de seguridad del gestor documental
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
WorkingDirectory=$PROJECT_DIR
ExecStart=/bin/bash $PROJECT_DIR/ops/backup.sh"

install_unit "gestor-backup.timer" "[Unit]
Description=Copia diaria del gestor documental

[Timer]
OnCalendar=*-*-* 02:30:00
RandomizedDelaySec=15m
Persistent=true

[Install]
WantedBy=timers.target"

install_unit "gestor-backup-verify.service" "[Unit]
Description=Prueba de restauracion del gestor documental
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
WorkingDirectory=$PROJECT_DIR
ExecStart=/bin/bash $PROJECT_DIR/ops/verify-backup.sh"

install_unit "gestor-backup-verify.timer" "[Unit]
Description=Prueba semanal de restauracion del gestor documental

[Timer]
OnCalendar=Sun *-*-* 04:00:00
RandomizedDelaySec=30m
Persistent=true

[Install]
WantedBy=timers.target"

install_unit "gestor-monitor.service" "[Unit]
Description=Monitor del gestor documental
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
WorkingDirectory=$PROJECT_DIR
ExecStart=/bin/bash $PROJECT_DIR/ops/monitor.sh"

install_unit "gestor-monitor.timer" "[Unit]
Description=Monitor periodico del gestor documental

[Timer]
OnBootSec=2m
OnUnitActiveSec=5m
AccuracySec=30s

[Install]
WantedBy=timers.target"

systemctl daemon-reload
systemctl enable --now gestor-backup.timer gestor-backup-verify.timer gestor-monitor.timer

echo "Mantenimiento instalado para: $PROJECT_DIR"
systemctl list-timers 'gestor-*' --no-pager
