# Gestor Documental para gestorÃ­a

AplicaciÃ³n web desarrollada con Spring Boot para centralizar la gestiÃ³n documental de una gestorÃ­a especializada en trÃ¡mites de vehÃ­culos.

El sistema permite gestionar solicitudes, expedientes, clientes, documentaciÃ³n, incidencias, mensajes e historial de actividad desde una plataforma Ãºnica. AdemÃ¡s, incorpora procesamiento de documentos PDF mediante OCR para ayudar a clasificar y dividir automÃ¡ticamente documentaciÃ³n escaneada.

## Funcionalidades principales

- AutenticaciÃ³n de usuarios con Spring Security.
- Roles diferenciados para administradores y clientes.
- Panel de administraciÃ³n con gestiÃ³n de usuarios, clientes, solicitudes y expedientes.
- Ãrea privada de cliente para crear solicitudes, consultar expedientes y aportar documentaciÃ³n.
- ConversiÃ³n de solicitudes en expedientes.
- GestiÃ³n de estados de solicitudes y expedientes.
- Subida y almacenamiento de documentos asociados a solicitudes o expedientes.
- Registro y resoluciÃ³n de incidencias.
- MensajerÃ­a e historial de actividad vinculados a los trÃ¡mites.
- Procesamiento OCR con Tesseract/Tess4J.
- DivisiÃ³n y clasificaciÃ³n de PDFs mediante Apache PDFBox.
- Despliegue mediante Docker y MySQL.

## Stack tecnolÃ³gico

- Java 17
- Spring Boot 4
- Spring MVC
- Spring Security
- Spring Data JPA / Hibernate
- React
- TypeScript
- Vite
- MySQL 8.4
- Maven
- Apache PDFBox
- Tess4J / Tesseract OCR
- Docker / Docker Compose

## Estructura del proyecto

```text
src/main/java/com/example/gestor_documental
|-- config          # ConfiguraciÃ³n e inicializaciÃ³n de datos
|-- controller      # API REST, descarga/visualizacion de documentos y fallback SPA
|-- dto             # Objetos de transferencia para API y formularios
|-- enums           # Estados, roles y tipos del dominio
|-- exception       # Excepciones y manejador global
|-- model           # Entidades JPA
|-- repository      # Repositorios Spring Data
|-- security        # ConfiguraciÃ³n y servicios de seguridad
|-- service         # Interfaces de servicios
|-- service/impl    # ImplementaciÃ³n de lÃ³gica de negocio
`-- validation      # Validadores de formularios
```

El frontend React se encuentra en:

```text
frontend
```


En Docker, el build de React se copia dentro del jar como recursos estaticos para servir la SPA desde Spring Boot.

## Requisitos

Para ejecutar el proyecto en local:

- JDK 17 o superior.
- Maven o el wrapper incluido (`mvnw` / `mvnw.cmd`).
- MySQL.
- Tesseract OCR con datos de idioma espaÃ±ol si se va a usar OCR.

Para ejecutar con Docker:

- Docker.
- Docker Compose.

## ConfiguraciÃ³n local

El perfil de desarrollo utiliza MySQL en local. La URL por defecto apunta a la base de datos `gestor_documental`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/gestor_documental
```

Configura las credenciales mediante variables de entorno:

```powershell
$env:DB_USER="root"
$env:DB_PASS="tu_password"
```

Si quieres crear un administrador automÃ¡ticamente al arrancar con el perfil `dev`, define tambiÃ©n:

```powershell
$env:DEV_ADMIN_EMAIL="admin.local@example.com"
$env:DEV_ADMIN_PASSWORD="cambia_este_valor"
```

TambiÃ©n puede configurarse la ruta de Tesseract:

```powershell
$env:TESSDATA_PATH="C:/Tesseract/Tesseract-OCR/tessdata"
```

## EjecuciÃ³n en local

En Windows:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

En Linux/macOS:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La aplicaciÃ³n se levanta por defecto en:

```text
http://localhost:8080
```

## EjecuciÃ³n con Docker

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

| Variable | DescripciÃ³n |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring (`dev` o `prod`). |
| `MYSQL_DATABASE` | Nombre de la base de datos MySQL. |
| `MYSQL_USER` | Usuario de base de datos usado por la aplicaciÃ³n. |
| `MYSQL_PASSWORD` | ContraseÃ±a del usuario de base de datos. |
| `MYSQL_ROOT_PASSWORD` | ContraseÃ±a del usuario root de MySQL. |
| `APP_ADMIN_EMAIL` | Email del administrador inicial. |
| `APP_ADMIN_PASSWORD` | ContraseÃ±a del administrador inicial. |
| `DEV_ADMIN_EMAIL` | Email opcional para crear un administrador en perfil `dev`. |
| `DEV_ADMIN_PASSWORD` | ContraseÃ±a opcional para crear un administrador en perfil `dev`. |
| `UPLOAD_DIR` | Directorio donde se guardan los archivos subidos. |
| `UPLOAD_ALLOWED_EXTENSIONS` | Extensiones permitidas para subida de documentos. |
| `MAX_FILE_SIZE` | TamaÃ±o mÃ¡ximo de archivo. |
| `MAX_REQUEST_SIZE` | TamaÃ±o mÃ¡ximo de peticiÃ³n multipart. |
| `LOG_FILE` | Ruta del fichero de logs. |
| `TESSERACT_DATAPATH` | Ruta al directorio `tessdata` de Tesseract. |
| `TESSDATA_PATH` | Ruta alternativa usada por el perfil local para localizar `tessdata`. |
| `COOKIE_SECURE` | Activa cookies seguras. Usar `true` en HTTPS y `false` en local sin HTTPS. |

## OCR y procesamiento documental

El sistema utiliza Tess4J como integraciÃ³n Java con Tesseract OCR. Cuando se sube documentaciÃ³n escaneada, el servicio OCR puede extraer texto de las pÃ¡ginas y ayudar a identificar tipos documentales.

Para el tratamiento de PDFs se utiliza Apache PDFBox. La aplicaciÃ³n permite trabajar con documentos completos y dividirlos en documentos mÃ¡s pequeÃ±os cuando se detectan bloques o pÃ¡ginas correspondientes a distintos tipos de documento.

Tipos documentales contemplados actualmente:

- DNI
- Contrato de compraventa
- Permiso de circulaciÃ³n
- Ficha tÃ©cnica
- Mandato
- Factura
- Expediente completo
- Mandato de representaciÃ³n
- Cambio de titularidad
- AutorizaciÃ³n SerafÃ­n
- Huella de trÃ¡mite
- Otros

## Seguridad y roles

La aplicaciÃ³n distingue dos roles principales:

- `ADMIN`: gestiona clientes, usuarios, solicitudes, expedientes, documentos e incidencias.
- `CLIENTE`: puede crear solicitudes, consultar sus expedientes y aportar documentaciÃ³n.

Las vistas y operaciones se filtran segÃºn el usuario autenticado. Los clientes solo deben acceder a la informaciÃ³n asociada a su propio perfil.

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

## API y monitorizaciÃ³n

El proyecto incluye:

- Spring Boot Actuator para endpoints de salud e informaciÃ³n.
- Springdoc OpenAPI UI para documentaciÃ³n de endpoints cuando estÃ© habilitada.

Endpoints habituales:

```text
/actuator/health
/swagger-ui.html
```

## Notas de desarrollo

- La lÃ³gica de negocio debe mantenerse en la capa de servicios.
- Los controladores deben limitarse a coordinar formularios, vistas y llamadas a servicios.
- No deben incluirse credenciales reales en el repositorio.
- Los archivos subidos y logs se montan como volÃºmenes en Docker:

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
