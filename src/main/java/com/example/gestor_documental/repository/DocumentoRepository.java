package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Documento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    List<Documento> findByExpedienteId(Long expedienteId);

    List<Documento> findBySolicitudId(Long solicitudId);

    @EntityGraph(attributePaths = {
            "expediente",
            "expediente.cliente",
            "solicitud",
            "solicitud.cliente"
    })
    @Query("select d from Documento d where d.id = :id")
    Optional<Documento> findByIdConRelaciones(@Param("id") Long id);


}
