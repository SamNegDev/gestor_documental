# Gestor Documental - Spring Boot Project 📂💻

Este es un proyecto de **Gestión Documental** desarrollado con **Spring Boot**, que integra capacidades de reconocimiento óptico de caracteres (**OCR**) usando **Tesseract**, además de una interfaz basada en **Thymeleaf** y una base de datos gestionada por **MySQL**. Su objetivo principal es facilitar el almacenamiento, visualización y procesamiento de documentos electrónicos.

---

## 🚀 Tecnologías utilizadas

- **Java 23** (Java Development Kit)
- **Spring Boot**: Framework que facilita la creación de aplicaciones web y RESTful.
- **MySQL**: Sistema de gestión de bases de datos relacional.
- **Tesseract**: Motor OCR para reconocimiento de texto.
- **Thymeleaf**: Motor de plantillas para la capa de presentación HTML.
- **Maven**: Herramienta de gestión de dependencias y construcción.

---

## ✨ Funcionalidades principales

- Carga de documentos con configuraciones avanzadas de tamaño y formato.
- Procesamiento OCR para extraer texto de imágenes o PDF.
- Gestión eficiente de documentos en la base de datos MySQL.
- Interfaz interactiva y dinámica basada en Thymeleaf.
- Logs detallados para monitoreo del sistema (Hibernate y Spring).

---

## 📁 Configuración del entorno

Asegúrate de tener los siguientes requisitos antes de proceder:

### **Requisitos previos**
1. **Instalación de Java**:
    - JDK (versión 23 o superior) configurado en tu sistema.
2. **Servidor MySQL**:
    - Crea una base de datos llamada `gestor_documental`.
3. **Tesseract OCR**:
    - Instala Tesseract y asegúrate de configurar su ruta en el archivo de propiedades.
    - Puedes descargarlo desde [Tesseract OCR](https://github.com/tesseract-ocr/tesseract).

### **Variables de entorno necesarias**
Asegúrate de configurar las siguientes variables de entorno en tu sistema:

- **`DB_USER`**: Usuario de la base de datos (predeterminado: `root`).
- **`DB_PASS`**: Contraseña de la base de datos (predeterminado: `samuelin1`).
- **`TESSDATA_PATH`**: Ruta a los archivos de idioma de Tesseract (por ejemplo: `C:/Tesseract/Tesseract-OCR/tessdata`).

### **Configuración en application.properties**
Configura el archivo `application.properties` para personalizar parámetros del sistema. Aquí hay un extracto de la configuración relevante:

```properties
# Configuración de la base de datos
spring.datasource.url=jdbc:mysql://localhost:3306/gestor_documental
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASS:samuelin1}

# Configuración de Tesseract OCR
tesseract.datapath=${TESSDATA_PATH:C:/Tesseract/Tesseract-OCR/tessdata}
app.ocr.language=spa
app.ocr.dpi=300

# Configuración de carga de archivos
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=50MB
```

---

## ⚙️ Ejecución del proyecto

Sigue estos pasos para ejecutar el proyecto localmente:

1. **Clona el repositorio**:
   ```bash
   git clone https://github.com/tu-usuario/gestor-documental.git
   cd gestor-documental
   ```

2. **Configura el entorno y credenciales** (archivo `application.properties` o mediante variables de entorno).

3. **Construye el proyecto con Maven**:
   ```bash
   mvn clean install
   ```

4. **Ejecuta la aplicación**:
   ```bash
   mvn spring-boot:run
   ```

5. **Abre la aplicación en tu navegador**:
   - URL por defecto: [http://localhost:8080](http://localhost:8080).

---

## 📒 Endpoints principales (ejemplo)

La aplicación cuenta con los siguientes endpoints:

| Método  | Endpoint              | Descripción                              |
|---------|-----------------------|------------------------------------------|
| `GET`   | `/`                   | Página principal de la aplicación.       |
| `POST`  | `/upload`             | Carga un documento al servidor.          |
| `GET`   | `/documents/{id}`     | Obtiene los detalles de un documento.    |
| `GET`   | `/documents/download` | Descarga un documento del servidor.      |

(Puedes personalizar esta sección con tus propios endpoints).

---

## 🧪 Pruebas

Para realizar pruebas, asegúrate de:
1. Configurar correctamente los datos en MySQL.
2. Cargar algunos documentos de ejemplo.
3. Invocar los endpoints mediante **Postman** o directamente desde la interfaz web.

---

## 📜 Licencia

Este proyecto está licenciado bajo los términos de [Tu licencia aquí] (ejemplo: MIT, Apache 2.0). 

---

## 📞 Contacto

Si tienes alguna duda o sugerencia, ¡no dudes en contactarnos!

- **Correo:** tu-correo@ejemplo.com
- **GitHub:** [TuUsuario](https://github.com/TuUsuario)