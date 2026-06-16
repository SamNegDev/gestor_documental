package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaPreviewResponse;
import com.example.gestor_documental.dto.seguimiento.NotificacionIncidenciaResponse;
import java.time.LocalDateTime;
import java.util.List;

public interface IncidenciaService {
    
    List<Incidencia> listarPorExpediente(Long expedienteId);
    List<Incidencia> listarPorSolicitud(Long solicitudId);
    
    Incidencia crearIncidenciaExpediente(Long expedienteId, Long tipoIncidenciaId, String observaciones, Usuario admin);
    Incidencia crearIncidenciaSolicitud(Long solicitudId, Long tipoIncidenciaId, String observaciones, Usuario admin);
    Incidencia prepararNotificacionExpediente(Long expedienteId, Usuario admin);
    
    void solicitarRevisionExpediente(Long expedienteId, Usuario cliente);
    void solicitarRevisionSolicitud(Long solicitudId, Usuario cliente);
    
    void resolverIncidencia(Long incidenciaId, Usuario admin);

    void reclamarIncidencia(Long incidenciaId, String observaciones, Usuario admin);

    void responderIncidenciaExpediente(Long incidenciaId, String respuesta, Usuario cliente);
    NotificacionIncidenciaPreviewResponse previsualizarNotificacion(Long incidenciaId, Usuario admin);
    NotificacionIncidenciaResponse notificarCliente(Long incidenciaId, String asunto, String mensaje, Usuario admin);
    NotificacionIncidenciaPreviewResponse previsualizarNotificacionWhatsapp(Long incidenciaId, Usuario admin);
    NotificacionIncidenciaResponse notificarClienteWhatsapp(Long incidenciaId, String mensaje, Usuario admin);
    void posponerSeguimiento(Long incidenciaId, LocalDateTime proximoAviso, Usuario admin);
    void archivarSeguimiento(Long incidenciaId, Usuario admin);
    void reactivarSeguimiento(Long incidenciaId, Usuario admin);
}
