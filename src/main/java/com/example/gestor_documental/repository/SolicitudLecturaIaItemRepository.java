package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoLecturaIaItem;
import com.example.gestor_documental.model.SolicitudLecturaIaItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SolicitudLecturaIaItemRepository extends JpaRepository<SolicitudLecturaIaItem, Long> {

    @EntityGraph(attributePaths = {"documento", "job"})
    List<SolicitudLecturaIaItem> findByJobIdOrderById(Long jobId);

    @Query("""
            select item from SolicitudLecturaIaItem item
            join fetch item.job job
            join fetch item.documento documento
            where documento.id in :documentoIds
              and item.id = (
                  select max(item2.id) from SolicitudLecturaIaItem item2
                  where item2.documento.id = documento.id
              )
            """)
    List<SolicitudLecturaIaItem> findUltimosPorDocumento(@Param("documentoIds") Collection<Long> documentoIds);

    boolean existsByDocumentoIdAndEstadoIn(Long documentoId, Collection<EstadoLecturaIaItem> estados);

    @Modifying
    @Query(value = """
            update solicitud_lectura_ia_item
            set documento_id = :documentoDestinoId
            where documento_id = :documentoOrigenId
            """, nativeQuery = true)
    int reasignarDocumento(
            @Param("documentoOrigenId") Long documentoOrigenId,
            @Param("documentoDestinoId") Long documentoDestinoId
    );
}
