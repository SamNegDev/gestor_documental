package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpedienteHaciendaDocumentacionService {

    private static final Set<String> HITOS_MODELO_620 = Set.of(
            "MODELO_620_PRESENTADO",
            "BATE_MODELO_620_PRESENTADO",
            "COM_MODELO_620_PRESENTADO"
    );

    private static final Set<TipoDocumento> DOCUMENTOS_VEHICULO = EnumSet.of(
            TipoDocumento.PERMISO_CIRCULACION,
            TipoDocumento.FICHA_TECNICA,
            TipoDocumento.INFORME_DGT
    );

    private static final Set<TipoDocumento> DOCUMENTOS_VENTA = EnumSet.of(
            TipoDocumento.CONTRATO_COMPRAVENTA,
            TipoDocumento.FACTURA
    );

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final DocumentoRepository documentoRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public boolean tieneDocumentacionHaciendaDisponible(ExpedienteDetailResponse detalle) {
        if (detalle == null || detalle.getSiguientePaso() == null) {
            return false;
        }
        String idSiguientePaso = detalle.getSiguientePaso().getId();
        return (idSiguientePaso != null
                && ("modelo-620-presentado".equals(idSiguientePaso) || idSiguientePaso.endsWith("-modelo-620")))
                || esHitoModelo620(detalle.getSiguientePaso().getAccion())
                || (detalle.getSiguientePaso().getAcciones() != null
                && detalle.getSiguientePaso().getAcciones().stream()
                .anyMatch(accion -> esHitoModelo620(accion.getCodigoHito())));
    }

    private boolean esHitoModelo620(String codigoHito) {
        return codigoHito != null && HITOS_MODELO_620.contains(codigoHito);
    }

    @Transactional(readOnly = true)
    public void escribirZipDocumentacionHacienda(List<Long> expedienteIds, Usuario admin, OutputStream outputStream) throws IOException {
        Path rutaBase = Paths.get(uploadDir).normalize().toAbsolutePath();
        Map<String, PaqueteHacienda> paquetes = new LinkedHashMap<>();
        List<String> incidencias = new ArrayList<>();

        for (Long expedienteId : expedienteIds) {
            ExpedienteDetailResponse detalle = expedienteDetalleApiService.obtenerDetalle(expedienteId, admin);
            List<String> faltas = new ArrayList<>();
            if (!tieneDocumentacionHaciendaDisponible(detalle)) {
                faltas.add("no esta en fase de presentar Modelo 620");
            }
            List<Documento> documentos = documentoRepository.findByExpedienteId(expedienteId).stream()
                    .filter(documento -> documento.getTipoDocumento() != null)
                    .sorted(Comparator.comparing(Documento::getFechaSubida, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            List<Path> vehiculo = rutasPdf(documentos, DOCUMENTOS_VEHICULO, rutaBase);
            List<Path> venta = rutasPdf(documentos, DOCUMENTOS_VENTA, rutaBase);
            if (vehiculo.isEmpty()) {
                faltas.add("falta documentacion del vehiculo");
            }
            PaqueteHacienda paquete;
            if (esBatecom(detalle)) {
                List<Path> ventaBate = rutasPdfOperacion(documentos, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE, rutaBase);
                List<Path> ventaCom = rutasPdfOperacion(documentos, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM, rutaBase);
                if (ventaBate.isEmpty()) {
                    faltas.add("falta contrato o factura BATE");
                }
                if (ventaCom.isEmpty()) {
                    faltas.add("falta contrato o factura COM");
                }
                paquete = PaqueteHacienda.batecom(vehiculo, ventaBate, ventaCom);
            } else {
                if (venta.isEmpty()) {
                    faltas.add("falta contrato o factura");
                }
                paquete = PaqueteHacienda.simple(vehiculo, venta);
            }
            if (faltas.isEmpty()) {
                paquetes.put(carpetaZip(detalle, expedienteId, paquetes.keySet()), paquete);
            } else {
                incidencias.add(identificadorExpediente(detalle, expedienteId) + ": " + String.join(", ", faltas));
            }
        }

        if (!incidencias.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede preparar la documentacion de Hacienda. Revisa:" + System.lineSeparator()
                            + "- " + String.join(System.lineSeparator() + "- ", incidencias));
        }

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, PaqueteHacienda> entry : paquetes.entrySet()) {
                escribirPdf(zip, entry.getKey() + "/documentacion_vehiculo.pdf", entry.getValue().vehiculo());
                if (entry.getValue().esBatecom()) {
                    escribirPdf(zip, entry.getKey() + "/contrato_factura_venta_BATE.pdf", entry.getValue().ventaBate());
                    escribirPdf(zip, entry.getKey() + "/contrato_factura_venta_COM.pdf", entry.getValue().ventaCom());
                } else {
                    escribirPdf(zip, entry.getKey() + "/contrato_factura_venta.pdf", entry.getValue().venta());
                }
            }
        }
    }

    private List<Path> rutasPdf(List<Documento> documentos, Set<TipoDocumento> tipos, Path rutaBase) {
        return documentos.stream()
                .filter(documento -> tipos.contains(documento.getTipoDocumento()))
                .map(documento -> rutaDocumento(documento, rutaBase))
                .filter(path -> path != null && Files.exists(path) && path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                .toList();
    }

    private List<Path> rutasPdfOperacion(List<Documento> documentos, TipoOperacionExpediente tipoOperacion, Path rutaBase) {
        return documentos.stream()
                .filter(documento -> DOCUMENTOS_VENTA.contains(documento.getTipoDocumento()))
                .filter(documento -> documento.getOperacion() != null && documento.getOperacion().getTipo() == tipoOperacion)
                .map(documento -> rutaDocumento(documento, rutaBase))
                .filter(path -> path != null && Files.exists(path) && path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                .toList();
    }

    private boolean esBatecom(ExpedienteDetailResponse detalle) {
        return TipoTramiteEnum.BATECOM.name().equals(detalle.getTipoTramite());
    }

    private Path rutaDocumento(Documento documento, Path rutaBase) {
        if (documento.getNombreArchivo() == null || documento.getNombreArchivo().isBlank()) {
            return null;
        }
        Path ruta = rutaBase.resolve(documento.getNombreArchivo()).normalize();
        return ruta.startsWith(rutaBase) ? ruta : null;
    }

    private void escribirPdf(ZipOutputStream zip, String nombreEntrada, List<Path> rutas) throws IOException {
        zip.putNextEntry(new ZipEntry(nombreEntrada));
        if (rutas.size() == 1) {
            Files.copy(rutas.get(0), zip);
        } else {
            ByteArrayOutputStream merged = new ByteArrayOutputStream();
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(merged);
            for (Path ruta : rutas) {
                merger.addSource(ruta.toFile());
            }
            merger.mergeDocuments(null);
            merged.writeTo(zip);
        }
        zip.closeEntry();
    }

    private String identificadorExpediente(ExpedienteDetailResponse detalle, Long expedienteId) {
        if (detalle != null && detalle.getMatricula() != null && !detalle.getMatricula().isBlank()) {
            return detalle.getMatricula() + " (EXP-" + expedienteId + ")";
        }
        return "EXP-" + expedienteId;
    }

    private String carpetaZip(ExpedienteDetailResponse detalle, Long expedienteId, Set<String> usadas) {
        String base = detalle.getMatricula() != null && !detalle.getMatricula().isBlank()
                ? nombreSeguro(detalle.getMatricula())
                : "EXP-" + expedienteId;
        return usadas.contains(base) ? base + "-EXP-" + expedienteId : base;
    }

    private String nombreSeguro(String nombre) {
        return nombre.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record PaqueteHacienda(List<Path> vehiculo, List<Path> venta, List<Path> ventaBate, List<Path> ventaCom) {
        static PaqueteHacienda simple(List<Path> vehiculo, List<Path> venta) {
            return new PaqueteHacienda(vehiculo, venta, List.of(), List.of());
        }

        static PaqueteHacienda batecom(List<Path> vehiculo, List<Path> ventaBate, List<Path> ventaCom) {
            return new PaqueteHacienda(vehiculo, List.of(), ventaBate, ventaCom);
        }

        boolean esBatecom() {
            return !ventaBate.isEmpty() || !ventaCom.isEmpty();
        }
    }
}
