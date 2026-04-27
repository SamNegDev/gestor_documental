package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.SolicitudService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MensajeServiceImpl implements MensajeService {

    private final MensajeRepository mensajeRepository;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;

    @Override
    public List<Mensaje> listarPorExpediente(Long expedienteId) {
        return mensajeRepository.findByExpedienteIdOrderByFechaCreacionAsc(expedienteId);
    }

    @Override
    public List<Mensaje> listarPorSolicitud(Long solicitudId) {
        return mensajeRepository.findBySolicitudIdOrderByFechaCreacionAsc(solicitudId);
    }

    @Override
    @Transactional
    public Mensaje añadirAExpediente(Long expedienteId, String contenido, Usuario autor) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        Mensaje mensaje = new Mensaje();
        if (!expedienteService.tienePermisoExpediente(expediente, autor)) {
            throw new AccesoDenegadoException("No tienes permiso para añadir mensajes a este expediente");
        }
        mensaje.setExpediente(expediente);
        mensaje.setContenido(contenido);
        mensaje.setAutor(autor);

        return mensajeRepository.save(mensaje);
    }

    @Override
    @Transactional
    public Mensaje añadirASolicitud(Long solicitudId, String contenido, Usuario autor) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        Mensaje mensaje = new Mensaje();
        if (!solicitudService.tienePermisoSolicitud(solicitud, autor)) {
            throw new AccesoDenegadoException("No tienes permiso para añadir mensajes a esta solicitud");
        }
        mensaje.setSolicitud(solicitud);
        mensaje.setContenido(contenido);
        mensaje.setAutor(autor);

        return mensajeRepository.save(mensaje);
    }
}
