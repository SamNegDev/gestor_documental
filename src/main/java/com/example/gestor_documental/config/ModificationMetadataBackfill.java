package com.example.gestor_documental.config;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ModificationMetadataBackfill implements ApplicationRunner {

    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final HistorialCambioRepository historialCambioRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        rellenarExpedientes();
        rellenarSolicitudes();
    }

    private void rellenarExpedientes() {
        List<Expediente> expedientes = expedienteRepository.findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();

        for (Expediente expediente : expedientes) {
            historialCambioRepository.findByExpedienteIdOrderByFechaCambioDesc(expediente.getId()).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            historial -> aplicarUltimoCambio(expediente, historial),
                            () -> aplicarCreacionComoUltimoCambio(expediente));
        }

        expedienteRepository.saveAll(expedientes);
    }

    private void rellenarSolicitudes() {
        List<Solicitud> solicitudes = solicitudRepository.findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();

        for (Solicitud solicitud : solicitudes) {
            historialCambioRepository.findBySolicitudIdOrderByFechaCambioDesc(solicitud.getId()).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            historial -> aplicarUltimoCambio(solicitud, historial),
                            () -> aplicarCreacionComoUltimoCambio(solicitud));
        }

        solicitudRepository.saveAll(solicitudes);
    }

    private void aplicarUltimoCambio(Expediente expediente, HistorialCambio historial) {
        expediente.setFechaUltimaModificacion(historial.getFechaCambio());
        expediente.setModificadoPor(historial.getUsuario());
    }

    private void aplicarCreacionComoUltimoCambio(Expediente expediente) {
        expediente.setFechaUltimaModificacion(expediente.getFechaCreacion());
        expediente.setModificadoPor(expediente.getCreadoPor());
    }

    private void aplicarUltimoCambio(Solicitud solicitud, HistorialCambio historial) {
        solicitud.setFechaUltimaModificacion(historial.getFechaCambio());
        solicitud.setModificadoPor(historial.getUsuario());
    }

    private void aplicarCreacionComoUltimoCambio(Solicitud solicitud) {
        solicitud.setFechaUltimaModificacion(solicitud.getFechaCreacion());
        solicitud.setModificadoPor(solicitud.getCreadoPor());
    }
}
