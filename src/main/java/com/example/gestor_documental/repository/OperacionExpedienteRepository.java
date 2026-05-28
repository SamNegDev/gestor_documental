package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.model.OperacionExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperacionExpedienteRepository extends JpaRepository<OperacionExpediente, Long> {
    List<OperacionExpediente> findByExpedienteIdOrderByOrdenAsc(Long expedienteId);

    Optional<OperacionExpediente> findByExpedienteIdAndTipo(Long expedienteId, TipoOperacionExpediente tipo);
}
