package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.CorreccionClasificacionDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorreccionClasificacionDocumentoRepository extends JpaRepository<CorreccionClasificacionDocumento, Long> {

    @Modifying
    @Query("update CorreccionClasificacionDocumento c set c.documento = null where c.documento.id = :documentoId")
    int desvincularDocumento(@Param("documentoId") Long documentoId);

    @Modifying
    @Query("delete from CorreccionClasificacionDocumento c where c.solicitud.id = :solicitudId")
    int eliminarPorSolicitud(@Param("solicitudId") Long solicitudId);
}
