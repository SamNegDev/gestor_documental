package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidenciaServiceImpl implements IncidenciaService {

    private final IncidenciaRepository incidenciaRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;
    private final TipoIncidenciaService tipoIncidenciaService;

    @Override
    public List<Incidencia> listarPorExpediente(Long expedienteId) {
        return incidenciaRepository.findByExpedienteId(expedienteId);
    }

    @Override
    public List<Incidencia> listarPorSolicitud(Long solicitudId) {
        return incidenciaRepository.findBySolicitudId(solicitudId);
    }

    @Override
    @Transactional
    public Incidencia crearIncidenciaExpediente(Long expedienteId, Long tipoIncidenciaId, String observaciones, Usuario admin) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
                
        if (!expedienteService.tienePermisoExpediente(expediente, admin) || admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede crear incidencias en expedientes.");
        }

        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("No se puede abrir una incidencia en un expediente finalizado");
        }

        TipoIncidencia tipo = tipoIncidenciaService.buscarPorId(tipoIncidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de incidencia no encontrado"));

        Incidencia incidencia = new Incidencia(tipo, expediente, observaciones, admin);
        incidenciaRepository.save(incidencia);

        // Cambiamos el estado a INCIDENCIA automáticamente
        expedienteService.cambiarEstado(expedienteId, EstadoExpediente.INCIDENCIA, admin);

        return incidencia;
    }

    @Override
    @Transactional
    public Incidencia crearIncidenciaSolicitud(Long solicitudId, Long tipoIncidenciaId, String observaciones, Usuario admin) {
        Solicitud solicitud = solicitudService.buscarPorId(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        if (!solicitudService.tienePermisoSolicitud(solicitud, admin) || admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede crear incidencias en solicitudes.");
        }

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA || solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede abrir incidencia en solicitud convertida o rechazada");
        }

        TipoIncidencia tipo = tipoIncidenciaService.buscarPorId(tipoIncidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de incidencia no encontrado"));

        Incidencia incidencia = new Incidencia(tipo, solicitud, observaciones, admin);
        incidenciaRepository.save(incidencia);

        solicitudService.cambiarEstadoSolicitud(solicitudId, EstadoSolicitud.PENDIENTE_DOCUMENTACION, admin);

        return incidencia;
    }

    @Override
    @Transactional
    public void solicitarRevisionExpediente(Long expedienteId, Usuario cliente) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
                
        if (!expedienteService.tienePermisoExpediente(expediente, cliente)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }

        List<Incidencia> activas = incidenciaRepository.findByExpedienteIdAndResueltaFalse(expediente.getId());
        for (Incidencia i : activas) {
            i.setResuelta(true);
            i.setFechaResolucion(LocalDateTime.now());
            i.setResueltoPor(cliente);
        }
        incidenciaRepository.saveAll(activas);

        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA) {
            // El propio administrador lo marca EN_TRAMITE manualmente si necesita o aqui?
            // Aquí lo está mandando el cliente, entonces un ADMIN no tiene que usar cambiarEstado (como es admin only en Service).
            // ¡Espera! El metodo cambiarEstado de expedienteService exige ser ADMIN.
            // Asi que lo hacemos manualmente en el objeto:
            expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
            expedienteService.guardar(expediente);
        }
    }

    @Override
    @Transactional
    public void solicitarRevisionSolicitud(Long solicitudId, Usuario cliente) {
        Solicitud solicitud = solicitudService.buscarPorId(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        if (!solicitudService.tienePermisoSolicitud(solicitud, cliente)) {
            throw new AccesoDenegadoException("No tienes permiso sobre esta solicitud");
        }

        List<Incidencia> activas = incidenciaRepository.findBySolicitudIdAndResueltaFalse(solicitud.getId());
        for (Incidencia i : activas) {
            i.setResuelta(true);
            i.setFechaResolucion(LocalDateTime.now());
            i.setResueltoPor(cliente);
        }
        incidenciaRepository.saveAll(activas);

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.PENDIENTE_DOCUMENTACION) {
            // Al igual que con expediente, actualizarlo directamente puesto que cambiarEstado exige ADMIN a veces
            solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
            solicitudService.guardar(solicitud);
        }
    }

    @Override
    @Transactional
    public void resolverIncidencia(Long incidenciaId, Usuario admin) {
        if (admin.getRolUsuario() != com.example.gestor_documental.enums.RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede resolver incidencias manualmente.");
        }

        Incidencia incidencia = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Incidencia no encontrada"));

        if (incidencia.isResuelta()) {
            throw new OperacionInvalidaException("La incidencia ya está resuelta.");
        }

        incidencia.setResuelta(true);
        incidencia.setFechaResolucion(LocalDateTime.now());
        incidencia.setResueltoPor(admin);
        incidenciaRepository.save(incidencia);
    }
}
