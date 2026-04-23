# 📂 Gestor Documental

El **Gestor Documental** es una plataforma integral diseñada para la administración automatizada de trámites (Expedientes y Solicitudes), enfocada fuertemente en el **procesamiento inteligente de adjuntos**, con soporte para reconocimiento óptico de caracteres (OCR) y manipulación de archivos PDF.

---

## 🛠️ Stack Tecnológico
*   **Backend:** Java 17+, Spring Boot 3
*   **Manejo de Datos:** Spring Data JPA (Hibernate)
*   **Seguridad:** Spring Security (Gestión de Roles: `ADMIN`, `CLIENTE`)
*   **Frontend (SSR):** Thymeleaf, Bootstrap 5, CSS nativo
*   **Procesamiento de Documentos:**
    *   **Tess4J (Tesseract OCR):** Lectura inteligente de archivos escaneados.
    *   **Apache PDFBox:** División, unión y extracción in-situ de páginas PDF (`PdfSplitService`).

---

## 📦 Arquitectura y Flujos Principales (Core)

El proyecto sigue una arquitectura sólida basada en el patrón **Controller - Service - Repository**. 

### 1. Ecosistema Expediente / Solicitud
*   **Solicitud:** Paso prospectivo inicial (normalmente iniciado por Clientes). Pasan por un proceso de revisión hasta ser marcadas como `CONVERTIDA` o `RECHAZADA`.
*   **Expediente:** Entidad formal superior. Cuando una Solicitud es aprobada, hereda sus datos planos, sus documentos mediante puentes lógicos y sus interesados (validando estrictamente la duplicidad de identidad vía DNI).

### 2. Motor Inteligente de Documentación (`OcrPdfService` & `DocumentoService`)
*   Al subir un único archivo bajo la etiqueta `EXPEDIENTE_COMPLETO`, el backend actúa como puente:
    1.  Guarda el PDF íntegro como resguardo o histórico.
    2.  Escanea cada página con **Tesseract**.
    3.  Aplica heurísticas por palabras clave para adivinar el contenido (DNI, Contrato Compraventa, Modelo Serafín, Ficha Técnica).
    4.  *(Auto-Mapeo)*: Agrupa las páginas contiguas que traten de lo mismo y segmenta dinámicamente el PDF grande en varios sub-documentos pequeños clasificados automáticamente.

### 3. Máquina de Estados de Incidencias (`IncidenciaService`)
El sistema está protegido criptográficamente contra el avance de expedientes rotos. Al crearse una `Incidencia`, una máquina de estados oculta toma el control:
*   Fuerza el estado de la Solicitud a `PENDIENTE_DOCUMENTACION`.
*   Bajo este estado, cualquier intento de un `ADMIN` por escalar a `FINALIZADO` o `EN_TRAMITE` será bloqueado por las reglas de negocio hasta que la incidencia esté marcada manualmente como `resuelta = true`.

### 4. Roles y Seguridad
*   **ADMIN**: Creadores, editores supremos e impulsores de flujos. (Pueden resolver incidencias y purgar usuarios de la BD).
*   **CLIENTE**: Limitados estrictamente vía **Multi-Tenant (Inquilino Multi-Cliente)**. Solo pueden interactuar (listar Expedientes, solicitar revisiones) sobre aquellos datos cuyo `ClienteId` empate exactamente con el Cliente vinculado a su propia credencial de `Usuario`.

---

## ⚙️ Configuración y Despliegue Local

### Requisitos Previos
1. Java Development Kit (JDK) 17 o superior.
2. Apache Maven instalado (o usar el Wrapper `mvnw`).
3. Binarios/Data de **Tesseract OCR** descargados e inyectados localmente (Se requiere la ruta del `tessdata` en `OcrProperties` vía `application.properties`).

### Comandos de Arranque
```bash
# Limpiar e instalar dependencias
mvn clean install

# Compilar clases localmente
mvn compile

# Arrancar el servidor Spring Boot (Por defecto en http://localhost:8080)
mvn spring-boot:run
```

---

## ⚠️ Puntos Técnicos Críticos a tener en cuenta (Para Desarrolladores)

1.  **DANGER - Extracción Destructiva de Páginas:** La herramienta de separar páginas en el panel UI invoca a `extraerPaginasDocumento()`. Es importante notar que esta acción **NO es solo de lectura**; altera *físicamente* el PDF original restándole las páginas tomadas para evitar almacenamiento duplicado.
2.  **Desfase de Arrays en PDFs:** Las utilidades en pantalla y String utilizan base 1 (Página 1, 2, 3). Sin embargo, `PdfSplitService` requiere base 0. La corrección se hace silenciosamente en la capa de utilidades (`parseRangoPaginas`), no manipular directamente índices crudos con Apache PDFBox.
3.  **Lógica Oculta en Servicios:** Evita añadir validaciones complejas de BD en los Controllers. Toda decisión comercial (como el cifrado de contraseñas de Usuarios nuevos o la validación del "Interesado a Medias") reside documentada en la capa `@Service`.
