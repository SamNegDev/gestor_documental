package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.HistorialCambioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialCambioServiceImpl implements HistorialCambioService {

    private final HistorialCambioRepository historialCambioRepository;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;

    @Override
    public void registrarCambioExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion) {
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

        expediente.setFechaUltimaModificacion(fechaCambio);
        expediente.setModificadoPor(usuario);
        expedienteRepository.save(expediente);
    }

    @Override
    public void registrarCambioSolicitud(Solicitud solicitud, Usuario usuario, String accion, String descripcion) {
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

        solicitud.setFechaUltimaModificacion(fechaCambio);
        solicitud.setModificadoPor(usuario);
        solicitudRepository.save(solicitud);
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
