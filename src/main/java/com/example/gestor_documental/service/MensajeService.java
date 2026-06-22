package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.Usuario;

import java.util.List;

public interface MensajeService {
    List<Mensaje> listarPorExpediente(Long expedienteId);
    List<Mensaje> listarPorSolicitud(Long solicitudId);
    Mensaje añadirAExpediente(Long expedienteId, String contenido, Usuario autor);
    Mensaje añadirASolicitud(Long solicitudId, String contenido, Usuario autor);
    long contarNoLeidosExpediente(Long expedienteId, Usuario usuario);
    void marcarLeidosExpediente(Long expedienteId, Usuario usuario);
}
