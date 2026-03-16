package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {
}
