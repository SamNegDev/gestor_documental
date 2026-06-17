package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.CorreoEntranteProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorreoEntranteProcesadoRepository extends JpaRepository<CorreoEntranteProcesado, Long> {
    boolean existsByMessageId(String messageId);
}
