package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
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
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpedienteJustificanteFinalService {

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final DocumentoRepository documentoRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public void escribirZipJustificantesFinales(List<Long> expedienteIds, Usuario admin, OutputStream outputStream) throws IOException {
        Map<String, List<Documento>> documentosPorCarpeta = new LinkedHashMap<>();
        for (Long id : expedienteIds) {
            ExpedienteDetailResponse detalle = expedienteDetalleApiService.obtenerDetalle(id, admin);
            if (!"FINALIZADO".equals(detalle.getEstado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden descargar justificantes de expedientes finalizados");
            }
            List<Documento> documentos = documentosFinales(id);
            if (!tieneJustificanteDgt(documentos) || !tieneModelo620(documentos)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan justificantes finales en el expediente " + id);
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

    public boolean tieneJustificantesFinales(Long expedienteId, String estado) {
        if (!"FINALIZADO".equals(estado)) {
            return false;
        }
        List<Documento> documentos = documentosFinales(expedienteId);
        return tieneJustificanteDgt(documentos) && tieneModelo620(documentos);
    }

    public List<String> justificantesFinalesPendientes(Long expedienteId, String estado) {
        if (!"FINALIZADO".equals(estado)) {
            return List.of();
        }
        List<Documento> documentos = documentosFinales(expedienteId);
        java.util.ArrayList<String> pendientes = new java.util.ArrayList<>();
        if (!tieneJustificanteDgt(documentos)) {
            pendientes.add("DGT");
        }
        if (!tieneModelo620(documentos)) {
            pendientes.add("620");
        }
        return pendientes;
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
