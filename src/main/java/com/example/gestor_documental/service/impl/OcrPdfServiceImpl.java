package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OcrProperties;
import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.service.OcrPdfService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OcrPdfServiceImpl implements OcrPdfService {

    private static final Logger log = LoggerFactory.getLogger(OcrPdfServiceImpl.class);
    private static final Pattern BASTIDOR_PATTERN = Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b");
    private static final Pattern PRECIO_PATTERN = Pattern.compile("(?<!\\d)\\d{1,3}(?:\\.\\d{3})*,\\d{2}");
    private static final Pattern IDENTIFICADOR_PATTERN = Pattern.compile("\\b(?:[XYZ]\\s*\\d{7}\\s*[A-Z]|[A-Z]\\d{8}|\\d{8}[A-Z])\\b");
    private static final Pattern MATRICULA_PATTERN = Pattern.compile("\\b(?:E\\s*[- ]\\s*)?\\d{4}\\s*[- ]?\\s?[BCDFGHJKLMNPRSTVWXYZ]{3}\\b");
    private static final Pattern NIE_PATTERN = Pattern.compile("\\b[XYZ]\\s*\\d{7}\\s*[A-Z]\\b");
    private static final Pattern CODIGO_FICHA_TECNICA_PATTERN = Pattern.compile("\\b(?:a\\.1|a\\.2|b\\.1|c\\.i|c\\.v|d\\.1|d\\.2|e\\.1|f\\.1|f\\.2|f\\.3|g\\.1|j\\.1|k\\.1|l\\.2|p\\.1|p\\.2|s\\.1|v\\.7)\\b");

    private final OcrProperties ocrProperties;

    /**
     * Extrae y evalúa el texto OCR de cada página independiente de un PDF.
     * Magia principal: Agrupa de manera contigua las páginas. Si las páginas 1, 2 y 3 
     * se detectan como el mismo tipo (ej. CONTRATO), las empaqueta en un solo bloque 
     * en lugar de separarlas en 3 documentos individuales. Si no detecta el tipo, lo envía a OTROS.
     */
    @Override
    public List<DocumentoDetectadoDto> detectarDocumentos(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return List.of();
        }

        try (PDDocument document = PDDocument.load(archivo.getBytes())) {
            int totalPaginas = document.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(document);


            List<DocumentoDetectadoDto> resultado = new ArrayList<>();
            TipoDocumento tipoActual = null;
            List<Integer> paginasActuales = new ArrayList<>();
            long limiteProcesamiento = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(Math.max(1, ocrProperties.getMaxProcessingSeconds()));
            long inicio = System.nanoTime();
            String nombreArchivo = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "sin-nombre";
            log.info("OCR_DIAG inicio archivo={} paginas={} dpi={} timeoutPagina={} timeoutTotal={}",
                    nombreArchivo,
                    totalPaginas,
                    ocrProperties.getDpi(),
                    ocrProperties.getPageTimeoutSeconds(),
                    ocrProperties.getMaxProcessingSeconds());

            for (int i = 0; i < totalPaginas; i++) {
                if (System.nanoTime() > limiteProcesamiento) {
                    if (!paginasActuales.isEmpty()) {
                        resultado.add(new DocumentoDetectadoDto(
                                tipoActual != null ? tipoActual : TipoDocumento.OTROS,
                                new ArrayList<>(paginasActuales)
                        ));
                        paginasActuales.clear();
                    }
                    List<Integer> paginasPendientes = new ArrayList<>();
                    for (int pendiente = i; pendiente < totalPaginas; pendiente++) {
                        paginasPendientes.add(pendiente);
                    }
                    if (!paginasPendientes.isEmpty()) {
                        resultado.add(new DocumentoDetectadoDto(TipoDocumento.OTROS, paginasPendientes));
                    }
                    log.warn("OCR detenido tras superar {} segundos. Paginas restantes marcadas como OTROS.",
                            ocrProperties.getMaxProcessingSeconds());
                    log.warn("OCR_DIAG timeout-total archivo={} pagina={} paginasRestantes={}",
                            nombreArchivo,
                            i + 1,
                            totalPaginas - i);
                    break;
                }

                TipoDocumento tipoDetectado = detectarTipoDocumentoPagina(document, renderer, i, nombreArchivo);

                log.info("OCR_DIAG pagina-resultado archivo={} pagina={}/{} tipo={}",
                        nombreArchivo,
                        i + 1,
                        totalPaginas,
                        tipoDetectado != null ? tipoDetectado : "NO_DETECTADO");

                if (tipoDetectado != null) {
                    if (tipoActual == null) {
                        tipoActual = tipoDetectado;
                        paginasActuales.add(i);
                    } else if (tipoDetectado == tipoActual) {
                        // misma familia documental, sigue el bloque
                        paginasActuales.add(i);
                    } else {
                        resultado.add(new DocumentoDetectadoDto(
                                tipoActual,
                                new ArrayList<>(paginasActuales)
                        ));

                        tipoActual = tipoDetectado;
                        paginasActuales.clear();
                        paginasActuales.add(i);
                    }
                } else {
                    if (tipoActual == null || tipoActual != TipoDocumento.OTROS) {
                        if (!paginasActuales.isEmpty()) {
                            resultado.add(new DocumentoDetectadoDto(
                                    tipoActual,
                                    new ArrayList<>(paginasActuales)
                            ));
                            paginasActuales.clear();
                        }
                        tipoActual = TipoDocumento.OTROS;
                    }
                    paginasActuales.add(i);
                }
            }

            if (!paginasActuales.isEmpty()) {
                resultado.add(new DocumentoDetectadoDto(
                        tipoActual != null ? tipoActual : TipoDocumento.OTROS,
                        new ArrayList<>(paginasActuales)
                ));
            }

            if (resultado.isEmpty()) {
                List<Integer> todasLasPaginas = new ArrayList<>();
                for (int i = 0; i < totalPaginas; i++) {
                    todasLasPaginas.add(i);
                }
                resultado.add(new DocumentoDetectadoDto(TipoDocumento.OTROS, todasLasPaginas));
            }

            log.info("OCR_DIAG fin archivo={} duracionMs={} bloques={}",
                    nombreArchivo,
                    millisDesde(inicio),
                    resumenBloques(resultado));
            return resultado;

        } catch (IOException e) {
            throw new RuntimeException("Error leyendo el PDF para OCR", e);
        }
    }

    @Override
    public Optional<TipoDocumento> detectarTipoDocumento(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return Optional.empty();
        }
        if (esImagen(archivo)) {
            try {
                BufferedImage imagen = javax.imageio.ImageIO.read(archivo.getInputStream());
                if (imagen == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(detectarTipoDocumento(extraerTexto(imagen)));
            } catch (IOException exception) {
                log.debug("No se pudo leer la imagen para OCR", exception);
                return Optional.empty();
            }
        }
        List<TipoDocumento> detectados = detectarDocumentos(archivo).stream()
                .map(DocumentoDetectadoDto::getTipoDocumento)
                .filter(tipo -> tipo != null && tipo != TipoDocumento.OTROS)
                .distinct()
                .toList();
        return detectados.size() == 1 ? Optional.of(detectados.get(0)) : Optional.empty();
    }

    @Override
    public String extraerTextoCompleto(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return "";
        }
        try (PDDocument document = PDDocument.load(archivo.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder texto = new StringBuilder();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                texto.append(' ').append(extraerTextoPagina(document, renderer, i));
            }
            return normalizar(texto.toString());
        } catch (IOException exception) {
            log.warn("No se pudo extraer texto completo del PDF: {}", exception.getMessage());
            return "";
        }
    }

    private String extraerTextoPagina(PDDocument document, PDFRenderer renderer, int pageIndex) throws IOException {
        String textoEmbebido = extraerTextoEmbebido(document, pageIndex);
        if (!textoEmbebido.isBlank()) {
            return textoEmbebido;
        }

        return extraerTextoPaginaPorOcr(renderer, pageIndex, "extraccion-completa");
    }

    private TipoDocumento detectarTipoDocumentoPagina(PDDocument document, PDFRenderer renderer, int pageIndex, String nombreArchivo) throws IOException {
        long inicio = System.nanoTime();
        String textoEmbebido = extraerTextoEmbebido(document, pageIndex);
        TipoDocumento tipoEmbebido = detectarTipoDocumento(textoEmbebido);
        log.info("OCR_DIAG texto-embebido archivo={} pagina={} chars={} duracionMs={} tipo={} muestra={}",
                nombreArchivo,
                pageIndex + 1,
                textoEmbebido.length(),
                millisDesde(inicio),
                tipoEmbebido != null ? tipoEmbebido : "NO_DETECTADO",
                muestra(textoEmbebido));
        if (tipoEmbebido != null) {
            return tipoEmbebido;
        }

        ResultadoOcr resultadoOcr = detectarTipoDocumentoPorOcrConRotaciones(renderer, pageIndex, nombreArchivo);
        log.info("OCR_DIAG texto-ocr archivo={} pagina={} rotacion={} chars={} tipo={} muestra={}",
                nombreArchivo,
                pageIndex + 1,
                resultadoOcr.variante(),
                resultadoOcr.texto().length(),
                resultadoOcr.tipoDocumento() != null ? resultadoOcr.tipoDocumento() : "NO_DETECTADO",
                muestra(resultadoOcr.texto()));
        return resultadoOcr.tipoDocumento();
    }

    private ResultadoOcr detectarTipoDocumentoPorOcrConRotaciones(PDFRenderer renderer, int pageIndex, String nombreArchivo) throws IOException {
        float dpi = ocrProperties.getDpi();
        long inicioRender = System.nanoTime();
        BufferedImage imagen = renderer.renderImageWithDPI(
                pageIndex,
                dpi,
                ImageType.GRAY
        );
        long renderMs = millisDesde(inicioRender);

        ResultadoOcr mejor = leerImagenOcr(imagen, dpi, nombreArchivo, pageIndex, "0", renderMs);
        if (mejor.tipoDocumento() != null) {
            return mejor;
        }

        for (int rotacion : new int[]{90, 270, 180}) {
            ResultadoOcr resultado = leerImagenOcr(rotarImagen(imagen, rotacion), dpi, nombreArchivo, pageIndex, String.valueOf(rotacion), 0);
            if (resultado.tipoDocumento() != null) {
                return resultado;
            }
            if (resultado.texto().length() > mejor.texto().length()) {
                mejor = resultado;
            }
        }

        for (RecorteOcr recorte : List.of(
                new RecorteOcr("recorte-superior", 0.0, 0.56),
                new RecorteOcr("recorte-inferior", 0.44, 1.0))) {
            ResultadoOcr resultado = leerImagenOcr(
                    recortarImagen(imagen, recorte.inicioY(), recorte.finY()),
                    dpi,
                    nombreArchivo,
                    pageIndex,
                    recorte.nombre(),
                    0);
            if (resultado.tipoDocumento() != null) {
                return resultado;
            }
            if (resultado.texto().length() > mejor.texto().length()) {
                mejor = resultado;
            }
        }

        return mejor;
    }

    private String extraerTextoPaginaPorOcr(PDFRenderer renderer, int pageIndex, String nombreArchivo) throws IOException {
        float dpi = ocrProperties.getDpi();
        long inicioRender = System.nanoTime();
        BufferedImage imagen = renderer.renderImageWithDPI(
                pageIndex,
                dpi,
                ImageType.GRAY
        );
        long renderMs = millisDesde(inicioRender);
        long inicioOcr = System.nanoTime();
        String texto = extraerTexto(imagen, dpi, ocrProperties.getPageTimeoutSeconds(), nombreArchivo, pageIndex + 1);
        log.info("OCR_DIAG ocr-pagina archivo={} pagina={} renderMs={} ocrMs={} chars={}",
                nombreArchivo,
                pageIndex + 1,
                renderMs,
                millisDesde(inicioOcr),
                texto.length());
        return texto;
    }

    private ResultadoOcr leerImagenOcr(BufferedImage imagen, float dpi, String nombreArchivo, int pageIndex, String variante, long renderMs) {
        long inicioOcr = System.nanoTime();
        String texto = extraerTexto(imagen, dpi, ocrProperties.getPageTimeoutSeconds(), nombreArchivo, pageIndex + 1);
        TipoDocumento tipo = detectarTipoDocumento(texto);
        log.info("OCR_DIAG ocr-pagina archivo={} pagina={} variante={} renderMs={} ocrMs={} chars={} tipo={}",
                nombreArchivo,
                pageIndex + 1,
                variante,
                renderMs,
                millisDesde(inicioOcr),
                texto.length(),
                tipo != null ? tipo : "NO_DETECTADO");
        return new ResultadoOcr(texto, tipo, variante);
    }

    private BufferedImage rotarImagen(BufferedImage original, int grados) {
        int ancho = original.getWidth();
        int alto = original.getHeight();
        int anchoDestino = grados == 180 ? ancho : alto;
        int altoDestino = grados == 180 ? alto : ancho;
        int tipo = original.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_BYTE_GRAY : original.getType();
        BufferedImage rotada = new BufferedImage(anchoDestino, altoDestino, tipo);
        java.awt.Graphics2D graphics = rotada.createGraphics();
        try {
            if (grados == 90) {
                graphics.translate(alto, 0);
                graphics.rotate(Math.toRadians(90));
            } else if (grados == 180) {
                graphics.translate(ancho, alto);
                graphics.rotate(Math.toRadians(180));
            } else if (grados == 270) {
                graphics.translate(0, ancho);
                graphics.rotate(Math.toRadians(270));
            }
            graphics.drawImage(original, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rotada;
    }

    private BufferedImage recortarImagen(BufferedImage original, double inicioY, double finY) {
        int y = Math.max(0, (int) Math.round(original.getHeight() * inicioY));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * (finY - inicioY)));
        if (y + alto > original.getHeight()) {
            alto = original.getHeight() - y;
        }
        BufferedImage recorte = original.getSubimage(0, y, original.getWidth(), alto);
        int tipo = original.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_BYTE_GRAY : original.getType();
        BufferedImage copia = new BufferedImage(recorte.getWidth(), recorte.getHeight(), tipo);
        java.awt.Graphics2D graphics = copia.createGraphics();
        try {
            graphics.drawImage(recorte, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copia;
    }

    private record ResultadoOcr(String texto, TipoDocumento tipoDocumento, String variante) {
    }

    private record RecorteOcr(String nombre, double inicioY, double finY) {
    }

    private String extraerTextoEmbebido(PDDocument document, int pageIndex) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            return normalizar(stripper.getText(document));
        } catch (IOException e) {
            log.debug("No se pudo extraer texto embebido de la pagina {}", pageIndex + 1, e);
            return "";
        }
    }


    private String extraerTexto(BufferedImage imagen) {
        return extraerTexto(imagen, ocrProperties.getDpi(), ocrProperties.getPageTimeoutSeconds(), "imagen", 1);
    }

    private String extraerTexto(BufferedImage imagen, float dpi, int timeoutSeconds, String nombreArchivo, int pagina) {
        Path tempImage = null;

        try {
            tempImage = java.nio.file.Files.createTempFile("ocr-page-", ".png");
            javax.imageio.ImageIO.write(imagen, "png", tempImage.toFile());

            ProcessBuilder processBuilder = new ProcessBuilder(
                    ocrProperties.getTesseractPath(),
                    tempImage.toAbsolutePath().toString(),
                    "stdout",
                    "-l", ocrProperties.getLanguage(),
                    "--psm", "6",
                    "--oem", "1",
                    "-c", "user_defined_dpi=" + Math.max(70, Math.round(dpi))
            );

            if (ocrProperties.getTessdataPath() != null && !ocrProperties.getTessdataPath().isBlank()) {
                processBuilder.environment().put("TESSDATA_PREFIX", ocrProperties.getTessdataPath());
            }
            processBuilder.environment().putIfAbsent("OMP_THREAD_LIMIT", "1");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            boolean terminado = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            if (!terminado) {
                process.destroyForcibly();
                log.warn("Tesseract supero el limite de {} segundos al procesar una pagina OCR.",
                        timeoutSeconds);
                log.warn("OCR_DIAG timeout-pagina archivo={} pagina={} timeoutSeconds={} dpi={} imagen={}x{}",
                        nombreArchivo,
                        pagina,
                        timeoutSeconds,
                        dpi,
                        imagen.getWidth(),
                        imagen.getHeight());
                return "";
            }

            int exitCode = process.exitValue();
            String output;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            if (exitCode != 0) {
                log.warn("Tesseract devolvio codigo {} al procesar una pagina OCR. Salida: {}", exitCode, output);
                log.warn("OCR_DIAG error-tesseract archivo={} pagina={} exitCode={} salida={}",
                        nombreArchivo,
                        pagina,
                        exitCode,
                        muestra(output));
                return "";
            }

            return normalizar(output);

        } catch (IOException e) {
            log.warn("Error de entrada/salida ejecutando OCR: {}", e.getMessage());
            log.warn("OCR_DIAG error-io archivo={} pagina={} mensaje={}", nombreArchivo, pagina, e.getMessage());
            return "";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Proceso OCR interrumpido: {}", e.getMessage());
            log.warn("OCR_DIAG interrumpido archivo={} pagina={} mensaje={}", nombreArchivo, pagina, e.getMessage());
            return "";

        } finally {
            if (tempImage != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempImage);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private long millisDesde(long inicioNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
    }

    private String resumenBloques(List<DocumentoDetectadoDto> bloques) {
        if (bloques == null || bloques.isEmpty()) {
            return "[]";
        }
        return bloques.stream()
                .map(bloque -> bloque.getTipoDocumento() + ":" + paginasHumanas(bloque.getPaginas()))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private String paginasHumanas(List<Integer> paginas) {
        if (paginas == null || paginas.isEmpty()) {
            return "[]";
        }
        return paginas.stream()
                .map(pagina -> String.valueOf(pagina + 1))
                .collect(java.util.stream.Collectors.joining("-", "[", "]"));
    }

    private String muestra(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String limpio = texto.replaceAll("\\s+", " ").trim();
        return limpio.length() > 180 ? limpio.substring(0, 180) + "..." : limpio;
    }

    /**
     * Mapea un texto extraído vía Tesseract hacia un tipo de documento del sistema (Enum).
     * Se fundamenta en comprobar palabras clave recurrentes que definen el patrón del documento.
     */
    private TipoDocumento detectarTipoDocumento(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String t = normalizar(texto);

//        if (t.length() > 900) {
//            t = t.substring(0, 900);
//        }

        if (pareceAutorizacionSerafin(t)) {
            return TipoDocumento.AUTORIZACION_SERAFIN;
        }

        TipoDocumento tipoPorPuntuacion = detectarTipoDocumentoPorPuntuacion(t);
        if (tipoPorPuntuacion != null) {
            return tipoPorPuntuacion;
        }

        //Factura
        if ((t.contains("factura") || t.contains("factura venta"))){
            return TipoDocumento.FACTURA;
        }

        // Contrato
        if ((t.contains("contrato") && t.contains("compraventa")) ||
                t.contains("vehiculo usado entre particulares") ||
                t.contains("reunidos") ||
                t.contains("estipulaciones") ||
                pareceContratoCompraventaRellenado(t)) {
            return TipoDocumento.CONTRATO_COMPRAVENTA;
        }


        // Autorización
        if (pareceAutorizacionSerafin(t))
                {
            return TipoDocumento.AUTORIZACION_SERAFIN;
        }

        // Cambio de titularidad / notificación de venta
        if ((t.contains("cambio de titularidad") && t.contains("vehiculos")) ||
                t.contains("notificacion de venta") ||
                (t.contains("datos del comprador") &&
                t.contains("datos del vendedor"))) {
            return TipoDocumento.CAMBIO_TITULARIDAD;
        }

        // Mandato / representación
        if (t.contains("mandato con representacion") ||
                t.contains("mandato de representacion") ||
                (t.contains("mandato") && t.contains("representacion")) ||
                t.contains("gestor administrativo") ||
                t.contains("mandante") ||
                t.contains("mandatario")) {
            return TipoDocumento.MANDATO;
        }

        // Ficha técnica
        if ((t.contains("informe") && t.contains("dgt") && t.contains("vehiculo")) ||
                (t.contains("informe") && t.contains("registro de vehiculos")) ||
                t.contains("informe reducido del vehiculo") ||
                t.contains("informe completo del vehiculo")) {
            return TipoDocumento.INFORME_DGT;
        }

        if ((t.contains("numero de identificacion") && t.contains("clasificacion del vehiculo")) ||
                t.contains("tara maxima") ||
                t.contains("neumaticos") || t.contains("mtma") && t.contains("anchura") ||
                t.contains("vehiculo procedente de la c.e.e")) {
            return TipoDocumento.FICHA_TECNICA;
        }

        // DNI
        if (t.contains("documento nacional de identidad") ||
                t.contains("documento nacional identidad") ||
                t.contains("documento nacional jentidad") ||
                t.contains("national identity card") ||
                t.contains("idesp") ||
                (t.contains("dni") && (t.contains("apellidos") || t.contains("nombre")))) {
            return TipoDocumento.DNI;
        }
        // Permiso de circulación
        if ((t.contains("permiso") && t.contains("circulacion")) || t.contains("(i.1)") || t.contains("(c.1.1)") ||
                t.contains("descripcion de los codigos") || t.contains("documento valido si acompaña") ||
                t.contains("numero de matricula") && t.contains("fecha de primera matriculacion")) {
            return TipoDocumento.PERMISO_CIRCULACION;
        }

        return null;
    }

    private boolean pareceAutorizacionSerafin(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        if (texto.contains("hace entrega de la documentacion")) {
            return true;
        }
        if (!texto.contains("modelo de autorizacion")) {
            return false;
        }
        return texto.contains("serafin")
                || texto.contains("entrega de la documentacion")
                || texto.contains("documentacion completa")
                || texto.contains("firmada del vehiculo");
    }

    private TipoDocumento detectarTipoDocumentoPorPuntuacion(String texto) {
        int identidad = puntuarIdentidad(texto);
        int permiso = puntuarPermisoCirculacion(texto);
        int ficha = puntuarFichaTecnica(texto);

        if (identidad >= 6 && identidad >= permiso + 2 && identidad >= ficha + 2) {
            return TipoDocumento.DNI;
        }
        if (permiso >= 7 && permiso >= ficha) {
            return TipoDocumento.PERMISO_CIRCULACION;
        }
        if (ficha >= 7 && ficha > permiso) {
            return TipoDocumento.FICHA_TECNICA;
        }
        return null;
    }

    private int puntuarIdentidad(String texto) {
        String compacto = compactarParaPatrones(texto);
        int puntos = 0;
        puntos += puntosSiContiene(texto, 7,
                "documento nacional de identidad",
                "documento nacional identidad",
                "documento nacional jentidad",
                "national identity card",
                "certificado de registro de ciudadano",
                "certificado de registro de ciudadano de la union",
                "certificado de registro de ciudadano ue",
                "registro central de extranjeros");
        puntos += puntosSiContiene(texto, 6,
                "tarjeta de identidad de extranjero",
                "identidad de extranjero",
                "residente comunitario",
                "residencia comunitaria",
                "permiso de residencia",
                "direccion general de la policia");
        puntos += puntosSiContiene(texto, 6,
                "passaporto",
                "passport",
                "pasaporte");
        puntos += puntosSiContiene(texto, 3,
                "documento no valido para acreditar",
                "real decreto 240/2007",
                "extranjero certifica",
                "extranjeros certifica",
                "repubblica italiana",
                "republica italiana",
                "cognome",
                "surname",
                "given names");
        puntos += puntosSiContiene(texto, 2,
                "nacionalidad",
                "nationality",
                "idesp",
                "dni",
                "nie",
                "tie");
        if (compacto.contains("P<ITA") || compacto.contains("P<ESP")) {
            puntos += 5;
        }
        if (NIE_PATTERN.matcher(compacto).find()) {
            puntos += 5;
        }
        return puntos;
    }

    private int puntuarPermisoCirculacion(String texto) {
        String compacto = compactarParaPatrones(texto);
        int puntos = 0;
        if (texto.contains("permiso") && texto.contains("circulacion")) {
            puntos += 8;
        }
        puntos += puntosSiContiene(texto, 7,
                "documento valido si acompana itv",
                "documento valido si acompana",
                "descripcion de los codigos");
        puntos += puntosSiContiene(texto, 3,
                "numero de matricula",
                "fecha de primera matriculacion",
                "proxima itv",
                "itv en vigor");
        puntos += puntosSiContiene(texto, 2,
                "c.1.1",
                "c.1.2",
                "p.3",
                "d.1",
                "d.2",
                "gasolina",
                "diesel");
        if (MATRICULA_PATTERN.matcher(compacto).find()) {
            puntos += 2;
        }
        if (BASTIDOR_PATTERN.matcher(compacto).find()) {
            puntos += 2;
        }
        return puntos;
    }

    private int puntuarFichaTecnica(String texto) {
        int puntos = 0;
        puntos += puntosSiContiene(texto, 8,
                "tarjeta itv",
                "tarjeta i.t.v",
                "ficha tecnica",
                "tarjeta itv expedida",
                "tarjeta itv expedida por dgt");
        puntos += puntosSiContiene(texto, 5,
                "inspecciones tecnicas",
                "clasificacion del vehiculo",
                "vehiculo procedente de la c.e.e",
                "homologacion de tipo",
                "certifica que el vehiculo",
                "caracteristicas se resenan",
                "caracteristicas y resenan",
                "numero de homologacion");
        puntos += puntosSiContiene(texto, 3,
                "numero de identificacion",
                "numero de serie",
                "certificado n",
                "codigo descripcion",
                "registro de fabricantes",
                "firmas autorizadas",
                "fabricante del vehiculo",
                "tara maxima",
                "masa maxima",
                "masa maxima autorizada",
                "masa maxima tecnicamente admisible",
                "neumaticos",
                "mtma",
                "anchura",
                "longitud",
                "codigo nive",
                "diligencia de venta");
        puntos += puntosSiContiene(texto, 2,
                "certifica que el vehiculo",
                "caracteristicas tecnicas",
                "tarjeta itv expedida");
        long codigosFicha = CODIGO_FICHA_TECNICA_PATTERN.matcher(texto).results().limit(6).count();
        if (codigosFicha >= 5) {
            puntos += 5;
        }
        return puntos;
    }

    private int puntosSiContiene(String texto, int puntos, String... senales) {
        for (String senal : senales) {
            if (texto.contains(senal)) {
                return puntos;
            }
        }
        return 0;
    }

    private String compactarParaPatrones(String texto) {
        return texto.toUpperCase()
                .replaceAll("[^A-Z0-9<]", " ");
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }

        String limpio = java.text.Normalizer.normalize(texto.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return (" " + limpio + " ")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean pareceContratoCompraventaRellenado(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String compacto = texto.toUpperCase()
                .replaceAll("[^A-Z0-9,\\.]", " ");
        boolean tieneBastidor = BASTIDOR_PATTERN.matcher(compacto).find();
        boolean tienePrecio = PRECIO_PATTERN.matcher(compacto).find();
        long identificadores = IDENTIFICADOR_PATTERN.matcher(compacto).results().limit(3).count();
        boolean tieneVehiculo = texto.contains("audi")
                || texto.contains("bmw")
                || texto.contains("citroen")
                || texto.contains("dacia")
                || texto.contains("ford")
                || texto.contains("hyundai")
                || texto.contains("kia")
                || texto.contains("mercedes")
                || texto.contains("nissan")
                || texto.contains("opel")
                || texto.contains("peugeot")
                || texto.contains("renault")
                || texto.contains("seat")
                || texto.contains("toyota")
                || texto.contains("volkswagen")
                || texto.contains("vw")
                || texto.contains("matricula")
                || MATRICULA_PATTERN.matcher(compacto).find();
        return tieneBastidor && tienePrecio && identificadores >= 2 && tieneVehiculo;
    }

    private boolean esImagen(MultipartFile archivo) {
        String contentType = archivo.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        String nombre = archivo.getOriginalFilename();
        return nombre != null && nombre.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp)$");
    }
}
