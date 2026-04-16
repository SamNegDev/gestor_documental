package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Usuario;
import java.util.List;

public interface IncidenciaService {
    
    List<Incidencia> listarPorExpediente(Long expedienteId);
    List<Incidencia> listarPorSolicitud(Long solicitudId);
    
    Incidencia crearIncidenciaExpediente(Long expedienteId, Long tipoIncidenciaId, String observaciones, Usuario admin);
    Incidencia crearIncidenciaSolicitud(Long solicitudId, Long tipoIncidenciaId, String observaciones, Usuario admin);
    
    void solicitarRevisionExpediente(Long expedienteId, Usuario cliente);
    void solicitarRevisionSolicitud(Long solicitudId, Usuario cliente);
    
    void resolverIncidencia(Long incidenciaId, Usuario admin);
}
