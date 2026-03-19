package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByExpedienteId(Long expedienteId);


}
