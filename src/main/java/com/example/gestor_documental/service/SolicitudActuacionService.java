package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.SolicitudPreparacionAccionResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionTraspasoResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SolicitudActuacionService {

    private final SolicitudPreparacionTraspasoService solicitudPreparacionTraspasoService;

    public String siguienteActuacion(Solicitud solicitud, Usuario usuario) {
        if (solicitud.getExpediente() != null || solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA) {
            return "Sin acciones pendientes";
        }
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            return "Consultar resolucion";
        }
        try {
            SolicitudPreparacionTraspasoResponse preparacion = solicitudPreparacionTraspasoService.obtenerPreparacion(solicitud.getId(), usuario);
            SolicitudPreparacionAccionResponse accion = preparacion.siguienteAccion();
            if (accion != null && hasText(accion.titulo())) {
                return accion.titulo();
            }
        } catch (RuntimeException ignored) {
            // El listado no debe caer si la preparacion no puede calcularse para una fila concreta.
        }
        return switch (solicitud.getEstadoSolicitud()) {
            case PENDIENTE_DOCUMENTACION -> "Aportar documentacion";
            case REVISANDO_INCIDENCIAS -> "Documentacion en revision";
            case PENDIENTE_REVISION -> "Revisar solicitud";
            default -> "Consultar solicitud";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
