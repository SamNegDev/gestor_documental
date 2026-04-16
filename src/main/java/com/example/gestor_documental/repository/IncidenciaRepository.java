package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {
    List<Incidencia> findByExpedienteId(Long expedienteId);
    List<Incidencia> findBySolicitudId(Long solicitudId);
    List<Incidencia> findByExpedienteIdAndResueltaFalse(Long expedienteId);
    List<Incidencia> findBySolicitudIdAndResueltaFalse(Long solicitudId);
}
