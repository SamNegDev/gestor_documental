package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.CorreoEntranteProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorreoEntranteProcesadoRepository extends JpaRepository<CorreoEntranteProcesado, Long> {
    Optional<CorreoEntranteProcesado> findByMessageId(String messageId);
}
