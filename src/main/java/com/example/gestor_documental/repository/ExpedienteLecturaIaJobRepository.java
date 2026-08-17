package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoLecturaIaJob;
import com.example.gestor_documental.model.ExpedienteLecturaIaJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpedienteLecturaIaJobRepository extends JpaRepository<ExpedienteLecturaIaJob, Long> {
    Optional<ExpedienteLecturaIaJob> findTopByExpedienteIdOrderByFechaCreacionDescIdDesc(Long expedienteId);
    Optional<ExpedienteLecturaIaJob> findTopByExpedienteIdAndEstadoInOrderByFechaCreacionDescIdDesc(
            Long expedienteId, Collection<EstadoLecturaIaJob> estados);
    List<ExpedienteLecturaIaJob> findByEstadoInOrderByFechaCreacionAsc(Collection<EstadoLecturaIaJob> estados);
}
