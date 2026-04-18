package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.service.HistorialCambioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialCambioServiceImpl implements HistorialCambioService {

    private final HistorialCambioRepository historialCambioRepository;

    @Override
    public void registrarCambioExpediente(Expediente expediente, Usuario usuario, String accion, String descripcion) {
        HistorialCambio historial = new HistorialCambio(
                accion,
                descripcion,
                expediente,
                null,
                usuario
        );
        historialCambioRepository.save(historial);
    }

    @Override
    public void registrarCambioSolicitud(Solicitud solicitud, Usuario usuario, String accion, String descripcion) {
        HistorialCambio historial = new HistorialCambio(
                accion,
                descripcion,
                null,
                solicitud,
                usuario
        );
        historialCambioRepository.save(historial);
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
