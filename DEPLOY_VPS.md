# Despliegue en VPS

## 1. Preparar servidor

Instala Docker, Docker Compose, Nginx y Certbot en el VPS. Copia el repositorio al servidor y crea el fichero `.env` desde `.env.example`:

```bash
cp .env.example .env
nano .env
```

Cambia como minimo:

```env
MYSQL_PASSWORD=...
MYSQL_ROOT_PASSWORD=...
APP_ADMIN_EMAIL=...
APP_ADMIN_PASSWORD=...
COOKIE_SECURE=true
SPRING_PROFILES_ACTIVE=prod
```

## 2. Levantar la app

```bash
docker compose up -d --build
docker compose logs -f app
```

La aplicacion queda escuchando solo en `127.0.0.1:8080` del VPS. MySQL tambien queda limitado a `127.0.0.1:3306`, no expuesto publicamente.

## 3. Nginx

Ejemplo para `/etc/nginx/sites-available/gestor_documental`:

```nginx
server {
    server_name TU_DOMINIO.com;

    client_max_body_size 100M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Activar y comprobar:

```bash
ln -s /etc/nginx/sites-available/gestor_documental /etc/nginx/sites-enabled/
nginx -t
systemctl reload nginx
```

## 4. HTTPS

```bash
certbot --nginx -d TU_DOMINIO.com
```

Con HTTPS activo, deja `COOKIE_SECURE=true`.

## 5. Operacion

Ver estado:

```bash
docker compose ps
docker compose logs -f app
```

Actualizar despliegue:

```bash
git pull
docker compose up -d --build
```

Comprobar el estado real de la aplicacion:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

La respuesta debe contener `{"status":"UP"}`. Docker tambien utiliza este endpoint para marcar el contenedor `gestor_app` como saludable.

## 6. Copias de seguridad y monitorizacion

Las tareas incluidas guardan en cada copia:

- Volcado consistente de MySQL comprimido.
- Directorio completo de documentos `uploads`.
- Metadatos y sumas SHA-256.

Configura en `.env`:

```env
BACKUP_DIR=/var/backups/gestor_documental
BACKUP_RETENTION_DAYS=30
BACKUP_MAX_AGE_HOURS=36
DISK_USAGE_WARN_PERCENT=85
BACKUP_RCLONE_REMOTE=
MONITOR_SUCCESS_URL=
MONITOR_FAILURE_URL=
```

Antes de activar los temporizadores, crea y verifica la primera copia:

```bash
cd /opt/gestor_documental
sudo bash ops/backup.sh
sudo bash ops/verify-backup.sh
```

Si ambas operaciones terminan correctamente, instala las tareas automaticas:

```bash
sudo bash ops/install-vps-maintenance.sh
```

Se crean estos temporizadores de `systemd`:

- Copia diaria alrededor de las 02:30.
- Restauracion de prueba cada domingo alrededor de las 04:00.
- Comprobacion de contenedores, aplicacion, disco y copias cada cinco minutos.

Para volver a ejecutar manualmente los servicios instalados:

```bash
sudo systemctl start gestor-backup.service
sudo systemctl status gestor-backup.service --no-pager

sudo systemctl start gestor-backup-verify.service
sudo systemctl status gestor-backup-verify.service --no-pager
```

Consultar proximas ejecuciones y registros:

```bash
systemctl list-timers 'gestor-*' --no-pager
journalctl -u gestor-backup.service -n 100 --no-pager
journalctl -u gestor-backup-verify.service -n 100 --no-pager
journalctl -u gestor-monitor.service -n 100 --no-pager
```

Los scripts tambien se pueden ejecutar manualmente:

```bash
sudo bash ops/backup.sh
sudo bash ops/verify-backup.sh
sudo bash ops/monitor.sh
```

### Copia externa

Una copia almacenada solo en el mismo VPS no cubre la perdida completa del servidor. Para replicarla en un destino externo, instala y configura `rclone` y establece `BACKUP_RCLONE_REMOTE`, por ejemplo:

```env
BACKUP_RCLONE_REMOTE=onedrive:gestor-documental
```

Cuando esta variable tenga valor, la copia diaria solo se considerara correcta si tambien se replica en el destino externo.

### Alertas externas

`MONITOR_SUCCESS_URL` y `MONITOR_FAILURE_URL` son opcionales. Permiten conectar el monitor con un servicio de comprobaciones programadas mediante peticiones `GET`. Aunque no se configuren, los fallos quedan registrados en `journalctl` y el servicio termina con estado de error.

### Recuperacion manual

No restaures una copia sobre produccion sin conservar antes una copia del estado actual. Para una recuperacion, detiene `app`, descomprime `database.sql.gz` hacia MySQL, sustituye `uploads` por el archivo guardado y vuelve a levantar la aplicacion. La prueba semanal hace esta operacion en un contenedor MySQL temporal y nunca modifica la base de datos de produccion.
