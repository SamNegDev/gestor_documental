package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.historial.DetalleCambioHistorial;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;

import java.util.List;

public interface HistorialCambioService {

    void registrarCambioExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion);

    void registrarCambioExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion,
                                   List<DetalleCambioHistorial> detalles);

    void registrarComunicacionExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion);

    void registrarCambioSolicitud(Solicitud solicitud, Usuario usuario, String accion, String descripcion);

    void registrarCambioSolicitud(Solicitud solicitud, Usuario usuario, String accion, String descripcion,
                                  List<DetalleCambioHistorial> detalles);

    List<HistorialCambio> listarPorExpediente(Long expedienteId);

    List<HistorialCambio> listarPorSolicitud(Long solicitudId);
}
