package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.model.HitoExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HitoExpedienteRepository extends JpaRepository<HitoExpediente, Long> {
    List<HitoExpediente> findByExpedienteId(Long expedienteId);

    Optional<HitoExpediente> findByExpedienteIdAndCodigo(Long expedienteId, CodigoHitoExpediente codigo);

    boolean existsByExpedienteIdAndCodigo(Long expedienteId, CodigoHitoExpediente codigo);
}
