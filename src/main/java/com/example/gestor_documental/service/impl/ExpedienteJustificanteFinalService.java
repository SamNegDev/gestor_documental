package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.dto.expediente.RequisitoDocumentalResponse;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpedienteJustificanteFinalService {

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final DocumentoRepository documentoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public void escribirZipJustificantesFinales(List<Long> expedienteIds, Usuario admin, OutputStream outputStream) throws IOException {
        Map<String, List<Documento>> documentosPorCarpeta = new LinkedHashMap<>();
        for (Long id : expedienteIds) {
            ExpedienteDetailResponse detalle = expedienteDetalleApiService.obtenerDetalle(id, admin);
            if (!"FINALIZADO".equals(detalle.getEstado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden descargar justificantes de expedientes finalizados");
            }
            List<Documento> documentos = documentosFinales(id);
            List<String> pendientes = justificantesPendientes(detalle, documentos);
            if (!pendientes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan justificantes finales en el expediente " + id + ": " + String.join(", ", pendientes));
            }
            documentosPorCarpeta.put(carpetaZipExpediente(detalle, id, documentosPorCarpeta.keySet()), documentos);
        }

        Path rutaBase = Paths.get(uploadDir).normalize().toAbsolutePath();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (Map.Entry<String, List<Documento>> entry : documentosPorCarpeta.entrySet()) {
                for (Documento documento : entry.getValue()) {
                    Path rutaArchivo = rutaBase.resolve(documento.getNombreArchivo()).normalize();
                    if (!rutaArchivo.startsWith(rutaBase) || !Files.exists(rutaArchivo)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se encontro un justificante en disco");
                    }
                    zip.putNextEntry(new ZipEntry(entry.getKey() + "/" + nombreSeguro(documento.getNombreArchivoOriginal())));
                    Files.copy(rutaArchivo, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean tieneJustificantesFinales(Long expedienteId, String estado) {
        if (!"FINALIZADO".equals(estado)) {
            return false;
        }
        List<Documento> documentos = documentosFinales(expedienteId);
        return justificantesPendientes(expedienteId, documentos).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<String> justificantesFinalesPendientes(Long expedienteId, String estado) {
        if (!"FINALIZADO".equals(estado)) {
            return List.of();
        }
        List<Documento> documentos = documentosFinales(expedienteId);
        return justificantesPendientes(expedienteId, documentos);
    }

    private List<Documento> documentosFinales(Long expedienteId) {
        return documentoRepository.findByExpedienteId(expedienteId).stream()
                .filter(documento -> esJustificanteDgt(documento) || documento.getTipoDocumento() == TipoDocumento.MODELO_620)
                .sorted(Comparator.comparing(Documento::getTipoDocumento))
                .toList();
    }

    private boolean tieneJustificanteDgt(List<Documento> documentos) {
        return documentos.stream().anyMatch(this::esJustificanteDgt);
    }

    private boolean tieneModelo620(List<Documento> documentos) {
        return documentos.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MODELO_620);
    }

    private List<String> justificantesPendientes(ExpedienteDetailResponse detalle, List<Documento> documentos) {
        java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
        List<RequisitoDocumentalResponse> requisitosFinales = detalle.getRequisitosDocumentales().stream()
                .filter(requisito -> "MODELO_620".equals(requisito.getTipoDocumento())
                        || "COMPROBANTE_DGT".equals(requisito.getTipoDocumento())
                        || "HUELLA_TRAMITE".equals(requisito.getTipoDocumento()))
                .toList();

        if (!requisitosFinales.isEmpty()) {
            for (RequisitoDocumentalResponse requisito : requisitosFinales) {
                if ("OMITIDO".equals(requisito.getEstado())) {
                    continue;
                }
                if (!"APORTADO".equals(requisito.getEstado())) {
                    pendientes.add(etiquetaPendiente(requisito));
                }
            }
            return pendientes.stream().distinct().toList();
        }

        if (!tieneJustificanteDgt(documentos)) {
            pendientes.add("DGT");
        }
        if (!"NOTIFICACION_VENTA".equals(detalle.getTipoTramite()) && !tieneModelo620(documentos)) {
            pendientes.add("620");
        }
        return pendientes;
    }

    private List<String> justificantesPendientes(Long expedienteId, List<Documento> documentos) {
        java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
        List<RequisitoDocumentalExpediente> requisitosFinales = requisitoRepository.findByExpedienteIdOrderByIdAsc(expedienteId).stream()
                .filter(requisito -> requisito.getTipoDocumento() == TipoDocumento.MODELO_620
                        || requisito.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT
                        || requisito.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE)
                .toList();

        if (!requisitosFinales.isEmpty()) {
            for (RequisitoDocumentalExpediente requisito : requisitosFinales) {
                if (requisito.getEstado() == EstadoRequisitoDocumental.OMITIDO) {
                    continue;
                }
                if (requisito.getEstado() != EstadoRequisitoDocumental.APORTADO) {
                    pendientes.add(etiquetaPendiente(requisito));
                }
            }
            return pendientes.stream().distinct().toList();
        }

        Expediente expediente = expedienteRepository.findById(expedienteId).orElse(null);
        boolean requiereModelo = expediente == null
                || expediente.getTipoTramite() == null
                || expediente.getTipoTramite().getNombre() == null
                || !"NOTIFICACION_VENTA".equals(expediente.getTipoTramite().getNombre().name());
        if (!tieneJustificanteDgt(documentos)) {
            pendientes.add("DGT");
        }
        if (requiereModelo && !tieneModelo620(documentos)) {
            pendientes.add("620");
        }
        return pendientes;
    }

    private String etiquetaPendiente(RequisitoDocumentalResponse requisito) {
        String base = "MODELO_620".equals(requisito.getTipoDocumento()) ? "620" : "DGT";
        return requisito.getOperacionLabel() != null && !requisito.getOperacionLabel().isBlank()
                ? base + " " + requisito.getOperacionLabel()
                : base;
    }

    private String etiquetaPendiente(RequisitoDocumentalExpediente requisito) {
        String base = requisito.getTipoDocumento() == TipoDocumento.MODELO_620 ? "620" : "DGT";
        return requisito.getOperacion() != null && requisito.getOperacion().getTipo() != null
                ? base + " " + requisito.getOperacion().getTipo().getLabel()
                : base;
    }

    private boolean esJustificanteDgt(Documento documento) {
        return documento.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE
                || documento.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT;
    }

    private String carpetaZipExpediente(ExpedienteDetailResponse detalle, Long expedienteId, Set<String> carpetasUsadas) {
        String base = detalle.getMatricula() != null && !detalle.getMatricula().isBlank()
                ? nombreSeguro(detalle.getMatricula())
                : "EXP-" + expedienteId;
        return carpetasUsadas.contains(base) ? base + "-EXP-" + expedienteId : base;
    }

    private String nombreSeguro(String nombre) {
        String base = nombre != null && !nombre.isBlank() ? nombre : "documento.pdf";
        return base.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
