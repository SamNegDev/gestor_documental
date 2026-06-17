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

@Service
@RequiredArgsConstructor
public class OcrPdfServiceImpl implements OcrPdfService {

    private static final Logger log = LoggerFactory.getLogger(OcrPdfServiceImpl.class);

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

            for (int i = 0; i < totalPaginas; i++) {
                String textoPagina = extraerTextoPagina(document, renderer, i);
                TipoDocumento tipoDetectado = detectarTipoDocumento(textoPagina);

                log.debug("OCR pagina {} de {} detectada como {}", i + 1, totalPaginas, tipoDetectado);

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

        BufferedImage imagen = renderer.renderImageWithDPI(
                pageIndex,
                ocrProperties.getDpi(),
                ImageType.GRAY
        );
        return extraerTexto(imagen);
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
                    "-c", "user_defined_dpi=" + (int) ocrProperties.getDpi()
            );

            if (ocrProperties.getTessdataPath() != null && !ocrProperties.getTessdataPath().isBlank()) {
                processBuilder.environment().put("TESSDATA_PREFIX", ocrProperties.getTessdataPath());
            }
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            String output;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.warn("Tesseract devolvio codigo {} al procesar una pagina OCR. Salida: {}", exitCode, output);
                return "";
            }

            return normalizar(output);

        } catch (IOException e) {
            log.warn("Error de entrada/salida ejecutando OCR: {}", e.getMessage());
            return "";

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Proceso OCR interrumpido: {}", e.getMessage());
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

        //Factura
        if ((t.contains("factura") || t.contains("factura venta"))){
            return TipoDocumento.FACTURA;
        }

        // Contrato
        if ((t.contains("contrato") && t.contains("compraventa")) ||
                t.contains("vehiculo usado entre particulares") ||
                t.contains("reunidos") ||
                t.contains("estipulaciones")) {
            return TipoDocumento.CONTRATO_COMPRAVENTA;
        }


        // Autorización
        if (t.contains("modelo de autorizacion") && t.contains("serafin")
                || t.contains("hace entrega de la documentacion"))
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
        if ((t.contains("numero de identificacion") && t.contains("clasificacion del vehiculo")) ||
                t.contains("tara maxima") ||
                t.contains("neumaticos") || t.contains("mtma") && t.contains("anchura") ||
                t.contains("vehiculo procedente de la c.e.e")) {
            return TipoDocumento.FICHA_TECNICA;
        }

        // DNI
        if (t.contains("documento nacional de identidad") ||
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

    private boolean esImagen(MultipartFile archivo) {
        String contentType = archivo.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        String nombre = archivo.getOriginalFilename();
        return nombre != null && nombre.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp)$");
    }
}
