package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.ExpedienteInteresado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExpedienteInteresadoRepository extends JpaRepository<ExpedienteInteresado, Long> {

    Optional<ExpedienteInteresado> findByExpedienteIdAndInteresadoId(Long expedienteId, Long interesadoId);

    void deleteByExpedienteId(Long expedienteId);
}
