package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoRevisionGa;
import com.example.gestor_documental.model.ExtraccionGaRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExtraccionGaRevisionRepository extends JpaRepository<ExtraccionGaRevision, Long> {

    Optional<ExtraccionGaRevision> findByExpedienteId(Long expedienteId);

    List<ExtraccionGaRevision> findByEstadoOrderByFechaPreparadoAsc(EstadoRevisionGa estado);

    List<ExtraccionGaRevision> findByExpedienteIdIn(Collection<Long> expedienteIds);

    List<ExtraccionGaRevision> findByExpedienteIdInAndEstado(Collection<Long> expedienteIds, EstadoRevisionGa estado);
}
