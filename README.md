# Gestor Documental para gestoría

Aplicación web desarrollada con Spring Boot para centralizar la gestión documental de una gestoría especializada en trámites de vehículos.

El sistema permite gestionar solicitudes, expedientes, clientes, documentación, incidencias, mensajes e historial de actividad desde una plataforma única. Además, incorpora procesamiento de documentos PDF mediante OCR para ayudar a clasificar y dividir automáticamente documentación escaneada.

## Funcionalidades principales

- Autenticación de usuarios con Spring Security.
- Roles diferenciados para administradores y clientes.
- Panel de administración con gestión de usuarios, clientes, solicitudes y expedientes.
- Área privada de cliente para crear solicitudes, consultar expedientes y aportar documentación.
- Conversión de solicitudes en expedientes.
- Gestión de estados de solicitudes y expedientes.
- Subida y almacenamiento de documentos asociados a solicitudes o expedientes.
- Registro y resolución de incidencias.
- Mensajería e historial de actividad vinculados a los trámites.
- Procesamiento OCR con Tesseract/Tess4J.
- División y clasificación de PDFs mediante Apache PDFBox.
- Despliegue mediante Docker y MySQL.

## Stack tecnológico

- Java 17
- Spring Boot 4
- Spring MVC
- Spring Security
- Spring Data JPA / Hibernate
- Thymeleaf
- Thymeleaf Layout Dialect
- Bootstrap 5
- MySQL 8.4
- Maven
- Apache PDFBox
- Tess4J / Tesseract OCR
- Docker / Docker Compose

## Estructura del proyecto

```text
src/main/java/com/example/gestor_documental
|-- config          # Configuración e inicialización de datos
|-- controller      # Controladores MVC
|-- dto             # Objetos auxiliares para formularios y vistas
|-- enums           # Estados, roles y tipos del dominio
|-- exception       # Excepciones y manejador global
|-- model           # Entidades JPA
|-- repository      # Repositorios Spring Data
|-- security        # Configuración y servicios de seguridad
|-- service         # Interfaces de servicios
|-- service/impl    # Implementación de lógica de negocio
`-- validation      # Validadores de formularios
```

Las plantillas Thymeleaf se encuentran en:

```text
src/main/resources/templates
```

Los estilos y recursos estáticos se encuentran en:

```text
src/main/resources/static
```

## Requisitos

Para ejecutar el proyecto en local:

- JDK 17 o superior.
- Maven o el wrapper incluido (`mvnw` / `mvnw.cmd`).
- MySQL.
- Tesseract OCR con datos de idioma español si se va a usar OCR.

Para ejecutar con Docker:

- Docker.
- Docker Compose.

## Configuración local

El perfil de desarrollo utiliza MySQL en local. La URL por defecto apunta a la base de datos `gestor_documental`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/gestor_documental
```

Configura las credenciales mediante variables de entorno:

```powershell
$env:DB_USER="root"
$env:DB_PASS="tu_password"
```

Si quieres crear un administrador automáticamente al arrancar con el perfil `dev`, define también:

```powershell
$env:DEV_ADMIN_EMAIL="admin.local@example.com"
$env:DEV_ADMIN_PASSWORD="cambia_este_valor"
```

También puede configurarse la ruta de Tesseract:

```powershell
$env:TESSDATA_PATH="C:/Tesseract/Tesseract-OCR/tessdata"
```

## Ejecución en local

En Windows:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

En Linux/macOS:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La aplicación se levanta por defecto en:

```text
http://localhost:8080
```

## Ejecución con Docker

1. Copiar el archivo de ejemplo de variables:

```bash
cp .env.example .env
```

2. Revisar y cambiar los valores sensibles de `.env`, especialmente:

```text
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
APP_ADMIN_EMAIL
APP_ADMIN_PASSWORD
COOKIE_SECURE
```

3. Levantar los contenedores:

```bash
docker compose up --build
```

El servicio queda publicado en:

```text
http://localhost:8080
```

En el `docker-compose.yml` actual el puerto se expone solo en `127.0.0.1`, por lo que no queda abierto directamente a toda la red del host.

## Variables de entorno principales

| Variable | Descripción |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring (`dev` o `prod`). |
| `MYSQL_DATABASE` | Nombre de la base de datos MySQL. |
| `MYSQL_USER` | Usuario de base de datos usado por la aplicación. |
| `MYSQL_PASSWORD` | Contraseña del usuario de base de datos. |
| `MYSQL_ROOT_PASSWORD` | Contraseña del usuario root de MySQL. |
| `APP_ADMIN_EMAIL` | Email del administrador inicial. |
| `APP_ADMIN_PASSWORD` | Contraseña del administrador inicial. |
| `DEV_ADMIN_EMAIL` | Email opcional para crear un administrador en perfil `dev`. |
| `DEV_ADMIN_PASSWORD` | Contraseña opcional para crear un administrador en perfil `dev`. |
| `UPLOAD_DIR` | Directorio donde se guardan los archivos subidos. |
| `UPLOAD_ALLOWED_EXTENSIONS` | Extensiones permitidas para subida de documentos. |
| `MAX_FILE_SIZE` | Tamaño máximo de archivo. |
| `MAX_REQUEST_SIZE` | Tamaño máximo de petición multipart. |
| `LOG_FILE` | Ruta del fichero de logs. |
| `TESSERACT_DATAPATH` | Ruta al directorio `tessdata` de Tesseract. |
| `TESSDATA_PATH` | Ruta alternativa usada por el perfil local para localizar `tessdata`. |
| `COOKIE_SECURE` | Activa cookies seguras. Usar `true` en HTTPS y `false` en local sin HTTPS. |

## OCR y procesamiento documental

El sistema utiliza Tess4J como integración Java con Tesseract OCR. Cuando se sube documentación escaneada, el servicio OCR puede extraer texto de las páginas y ayudar a identificar tipos documentales.

Para el tratamiento de PDFs se utiliza Apache PDFBox. La aplicación permite trabajar con documentos completos y dividirlos en documentos más pequeños cuando se detectan bloques o páginas correspondientes a distintos tipos de documento.

Tipos documentales contemplados actualmente:

- DNI
- Contrato de compraventa
- Permiso de circulación
- Ficha técnica
- Mandato
- Factura
- Expediente completo
- Mandato de representación
- Cambio de titularidad
- Autorización Serafín
- Huella de trámite
- Otros

## Seguridad y roles

La aplicación distingue dos roles principales:

- `ADMIN`: gestiona clientes, usuarios, solicitudes, expedientes, documentos e incidencias.
- `CLIENTE`: puede crear solicitudes, consultar sus expedientes y aportar documentación.

Las vistas y operaciones se filtran según el usuario autenticado. Los clientes solo deben acceder a la información asociada a su propio perfil.

## Estados principales

Estados de solicitud:

- `PENDIENTE_REVISION`
- `CONVERTIDA`
- `RECHAZADO`
- `PENDIENTE_DOCUMENTACION`
- `REVISANDO_INCIDENCIAS`

Estados de expediente:

- `EN_TRAMITE`
- `INCIDENCIA`
- `FINALIZADO`
- `RECHAZADO`
- `ENVIADO_DGT`
- `REVISANDO_INCIDENCIAS`

## API y monitorización

El proyecto incluye:

- Spring Boot Actuator para endpoints de salud e información.
- Springdoc OpenAPI UI para documentación de endpoints cuando esté habilitada.

Endpoints habituales:

```text
/actuator/health
/swagger-ui.html
```

## Notas de desarrollo

- La lógica de negocio debe mantenerse en la capa de servicios.
- Los controladores deben limitarse a coordinar formularios, vistas y llamadas a servicios.
- No deben incluirse credenciales reales en el repositorio.
- Los archivos subidos y logs se montan como volúmenes en Docker:

```text
./uploads:/app/uploads
./logs:/app/logs
```

## Pruebas

Para ejecutar las pruebas:

```bash
./mvnw test
```

En Windows:

```powershell
.\mvnw.cmd test
```
