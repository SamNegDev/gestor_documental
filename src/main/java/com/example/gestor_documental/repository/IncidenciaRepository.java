package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {
    @Query("select i from Incidencia i left join fetch i.expediente e left join fetch e.cliente left join fetch i.tipoIncidencia")
    List<Incidencia> findAllWithDetails();

    List<Incidencia> findByExpedienteId(Long expedienteId);
    List<Incidencia> findBySolicitudId(Long solicitudId);
    List<Incidencia> findByExpedienteIdAndResueltaFalse(Long expedienteId);
    List<Incidencia> findBySolicitudIdAndResueltaFalse(Long solicitudId);
}
