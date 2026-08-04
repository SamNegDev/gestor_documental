package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.historial.DetalleCambioHistorial;
import com.example.gestor_documental.enums.TipoActividadHistorial;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.HistorialCambioDetalle;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HistorialCambioDetalleRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.HistorialCambioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialCambioServiceImpl implements HistorialCambioService {

    private final HistorialCambioRepository historialCambioRepository;
    private final HistorialCambioDetalleRepository historialCambioDetalleRepository;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;

    @Override
    @Transactional
    public void registrarCambioExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion) {
        registrarCambioExpediente(expediente, usuario, accion, descripcion, List.of());
    }

    @Override
    @Transactional
    public void registrarCambioExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion,
                                          List<DetalleCambioHistorial> detalles) {
        LocalDateTime fechaCambio = LocalDateTime.now();
        HistorialCambio historial = new HistorialCambio(
                accion,
                descripcion,
                expediente,
                null,
                usuario
        );
        historial.setFechaCambio(fechaCambio);
        historialCambioRepository.save(historial);
        guardarDetalles(historial, detalles);

        expediente.setFechaUltimaModificacion(fechaCambio);
        expediente.setModificadoPor(usuario);
        expedienteRepository.save(expediente);
    }

    @Override
    @Transactional
    public void registrarComunicacionExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion) {
        HistorialCambio historial = new HistorialCambio(
                accion,
                descripcion,
                expediente,
                null,
                usuario
        );
        historial.setTipoActividad(TipoActividadHistorial.COMUNICACION);
        historialCambioRepository.save(historial);
    }

    @Override
    @Transactional
    public void registrarCambioSolicitud(Solicitud solicitud, Usuario usuario, String accion, String descripcion) {
        registrarCambioSolicitud(solicitud, usuario, accion, descripcion, List.of());
    }

    @Override
    @Transactional
    public void registrarCambioSolicitud(Solicitud solicitud, Usuario usuario, String accion, String descripcion,
                                         List<DetalleCambioHistorial> detalles) {
        LocalDateTime fechaCambio = LocalDateTime.now();
        HistorialCambio historial = new HistorialCambio(
                accion,
                descripcion,
                null,
                solicitud,
                usuario
        );
        historial.setFechaCambio(fechaCambio);
        historialCambioRepository.save(historial);
        guardarDetalles(historial, detalles);

        solicitud.setFechaUltimaModificacion(fechaCambio);
        solicitud.setModificadoPor(usuario);
        solicitudRepository.save(solicitud);
    }

    private void guardarDetalles(HistorialCambio historial, List<DetalleCambioHistorial> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }
        List<HistorialCambioDetalle> entidades = detalles.stream()
                .filter(java.util.Objects::nonNull)
                .filter(detalle -> detalle.campo() != null && !detalle.campo().isBlank())
                .filter(detalle -> detalle.etiqueta() != null && !detalle.etiqueta().isBlank())
                .map(detalle -> {
                    HistorialCambioDetalle entidad = new HistorialCambioDetalle();
                    entidad.setHistorialCambio(historial);
                    entidad.setCampo(detalle.campo().trim());
                    entidad.setEtiqueta(detalle.etiqueta().trim());
                    entidad.setValorAnterior(detalle.valorAnterior());
                    entidad.setValorPosterior(detalle.valorPosterior());
                    return entidad;
                })
                .toList();
        if (!entidades.isEmpty()) {
            historialCambioDetalleRepository.saveAll(entidades);
        }
    }

    @Override
    public List<HistorialCambio> listarPorExpediente(Long expedienteId) {
        return historialCambioRepository.findByExpedienteIdOrderByFechaCambioDesc(expedienteId);
    }

    @Override
    public List<HistorialCambio> listarPorSolicitud(Long solicitudId) {
        return historialCambioRepository.findBySolicitudIdOrderByFechaCambioDesc(solicitudId);
    }
}
