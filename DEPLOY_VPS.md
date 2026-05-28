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

Backup basico de base de datos:

```bash
docker compose exec mysql mysqldump -u root -p gestor_documental > backup_gestor_documental.sql
```
