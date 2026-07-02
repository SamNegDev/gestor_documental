package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentoVehiculoLecturaRepository extends JpaRepository<DocumentoVehiculoLectura, Long> {

    @EntityGraph(attributePaths = {"documento"})
    Optional<DocumentoVehiculoLectura> findByDocumentoId(Long documentoId);

    @EntityGraph(attributePaths = {"documento"})
    List<DocumentoVehiculoLectura> findByDocumentoIdIn(Collection<Long> documentoIds);
}
