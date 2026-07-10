package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.DocumentoRolesLectura;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentoRolesLecturaRepository extends JpaRepository<DocumentoRolesLectura, Long> {

    @EntityGraph(attributePaths = {"documento", "vendedorInteresado", "compradorInteresado"})
    Optional<DocumentoRolesLectura> findByDocumentoId(Long documentoId);

    @EntityGraph(attributePaths = {"documento", "vendedorInteresado", "compradorInteresado"})
    List<DocumentoRolesLectura> findByDocumentoIdIn(Collection<Long> documentoIds);

    void deleteByDocumentoId(Long documentoId);
}
