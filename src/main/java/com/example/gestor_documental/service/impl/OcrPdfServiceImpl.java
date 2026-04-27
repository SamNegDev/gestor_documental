package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.config.OcrProperties;
import com.example.gestor_documental.dto.DocumentoDetectadoDto;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.service.OcrPdfService;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OcrPdfServiceImpl implements OcrPdfService {

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
            ITesseract tesseract = crearTesseract();

            List<DocumentoDetectadoDto> resultado = new ArrayList<>();
            TipoDocumento tipoActual = null;
            List<Integer> paginasActuales = new ArrayList<>();

            for (int i = 0; i < totalPaginas; i++) {
                BufferedImage imagen = renderer.renderImageWithDPI(
                        i,ocrProperties.getDpi(),
                        ImageType.GRAY
                );

                String textoPagina = extraerTexto(tesseract, imagen);
                TipoDocumento tipoDetectado = detectarTipoDocumento(textoPagina);

                System.out.println("========= PAGINA " + (i + 1) + " =========");

                System.out.println("TEXTO OCR (primeros 300 chars):");
                System.out.println(
                        textoPagina.length() > 300
                                ? textoPagina.substring(0, 300)
                                : textoPagina
                );

                System.out.println("TIPO DETECTADO: " + tipoDetectado);
                System.out.println("======================================");

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
                    if (tipoActual == null) {
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

    private ITesseract crearTesseract() {
        Tesseract tesseract = new Tesseract();

        tesseract.setDatapath(ocrProperties.getTessdataPath());
        tesseract.setLanguage(ocrProperties.getLanguage());

        // Más estable para documentos escaneados normales
        tesseract.setPageSegMode(6);

        // Motor LSTM
        tesseract.setOcrEngineMode(1);

        tesseract.setVariable("user_defined_dpi", String.valueOf((int) ocrProperties.getDpi()));

        return tesseract;
    }

    private String extraerTexto(ITesseract tesseract, BufferedImage imagen) {
        try {
            String texto = tesseract.doOCR(imagen);
            return normalizar(texto);
        } catch (TesseractException e) {
            return "";
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
                t.contains("gestor administrativo") ||
                t.contains("mandante") ||
                t.contains("mandatario")) {
            return TipoDocumento.MANDATO_REPRESENTACION;
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

        return (" " + texto.toLowerCase() + " ")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replaceAll("\\s+", " ")
                .trim();
    }
}