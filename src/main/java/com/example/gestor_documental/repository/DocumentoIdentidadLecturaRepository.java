package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentoIdentidadLecturaRepository extends JpaRepository<DocumentoIdentidadLectura, Long> {

    @EntityGraph(attributePaths = {"documento", "interesadoVinculado"})
    Optional<DocumentoIdentidadLectura> findByDocumentoId(Long documentoId);

    @EntityGraph(attributePaths = {"documento", "interesadoVinculado"})
    List<DocumentoIdentidadLectura> findByDocumentoIdIn(Collection<Long> documentoIds);

    void deleteByDocumentoId(Long documentoId);
}
