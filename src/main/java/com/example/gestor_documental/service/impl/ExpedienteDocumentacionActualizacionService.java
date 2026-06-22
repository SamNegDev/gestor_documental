package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ActualizacionDocumentalExpedienteResponse;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExpedienteDocumentacionActualizacionService {

    private static final Set<TipoDocumento> DOCUMENTOS_IDENTIDAD = EnumSet.of(TipoDocumento.DNI, TipoDocumento.CIF);
    private static final Set<TipoDocumento> DOCUMENTOS_OPERACION = EnumSet.of(TipoDocumento.CONTRATO_COMPRAVENTA, TipoDocumento.FACTURA);

    private final ExpedienteService expedienteService;
    private final DocumentoRepository documentoRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    private final DocumentoRolesLecturaService documentoRolesLecturaService;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalExpedienteService;
    private final HistorialCambioService historialCambioService;

    public ActualizacionDocumentalExpedienteResponse actualizarDesdeDocumentos(Long expedienteId, Usuario admin) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, admin)) {
            throw new AccesoDenegadoException("No tienes permiso para modificar este expediente");
        }

        List<Documento> documentos = documentoRepository.findByExpedienteId(expedienteId).stream()
                .filter(documento -> documento.getId() != null && documento.getTipoDocumento() != null)
                .sorted(Comparator.comparing(Documento::getFechaSubida, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        int identidadesLeidas = 0;
        int operacionesLeidas = 0;
        int datosAplicados = 0;
        List<String> avisos = new ArrayList<>();

        for (Documento documento : documentos) {
            if (!DOCUMENTOS_IDENTIDAD.contains(documento.getTipoDocumento())) {
                continue;
            }
            try {
                DocumentoIdentidadLecturaResponse lectura = documentoIdentidadLecturaService.leerIdentidad(documento.getId(), false, admin);
                identidadesLeidas++;
                if (lectura != null && lectura.isRequiereRevision()) {
                    avisos.add(nombreDocumento(documento) + ": identidad leida, requiere revision.");
                }
            } catch (RuntimeException exception) {
                avisos.add(nombreDocumento(documento) + ": no se pudo leer identidad (" + mensaje(exception) + ").");
            }
        }

        for (Documento documento : documentos) {
            if (!DOCUMENTOS_OPERACION.contains(documento.getTipoDocumento())) {
                continue;
            }
            try {
                DocumentoRolesLecturaResponse lectura = documentoRolesLecturaService.leerRoles(documento.getId(), false, admin);
                operacionesLeidas++;
                if (lectura == null || !lectura.isAplicable()) {
                    avisos.add(nombreDocumento(documento) + ": roles leidos, no aplicables automaticamente"
                            + motivo(lectura != null ? lectura.getMotivoAplicacion() : null) + ".");
                    continue;
                }
                documentoRolesLecturaService.aplicarDatos(documento.getId(), admin);
                datosAplicados++;
            } catch (RuntimeException exception) {
                avisos.add(nombreDocumento(documento) + ": no se pudieron aplicar datos (" + mensaje(exception) + ").");
            }
        }

        requisitoDocumentalExpedienteService.sincronizarYListar(
                expediente,
                expedienteInteresadoRepository.findByExpedienteId(expedienteId),
                documentoRepository.findByExpedienteId(expedienteId),
                admin
        );

        historialCambioService.registrarCambioExpediente(
                expediente,
                admin,
                "ACTUALIZACION DOCUMENTAL",
                "Se revisaron documentos existentes: " + identidadesLeidas + " identidades, "
                        + operacionesLeidas + " contratos/facturas, " + datosAplicados + " aplicaciones.");

        return ActualizacionDocumentalExpedienteResponse.builder()
                .identidadesLeidas(identidadesLeidas)
                .operacionesLeidas(operacionesLeidas)
                .datosAplicados(datosAplicados)
                .avisos(avisos)
                .build();
    }

    private String nombreDocumento(Documento documento) {
        return documento.getNombreArchivoOriginal() != null && !documento.getNombreArchivoOriginal().isBlank()
                ? documento.getNombreArchivoOriginal()
                : "documento " + documento.getId();
    }

    private String mensaje(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof OperacionInvalidaException && current.getMessage() != null) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return exception.getMessage() != null ? exception.getMessage() : "error no especificado";
    }

    private String motivo(String motivo) {
        return motivo != null && !motivo.isBlank() ? ": " + motivo : "";
    }
}
