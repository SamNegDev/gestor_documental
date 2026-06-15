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

    List<Documento> findByIncidenciaId(Long incidenciaId);

    List<Documento> findBySolicitudId(Long solicitudId);

    List<Documento> findByClienteIdOrderByFechaSubidaDesc(Long clienteId);

    List<Documento> findByClienteIdAndInteresadoIsNullOrderByFechaSubidaDesc(Long clienteId);

    List<Documento> findByClienteIdAndInteresadoIdOrderByFechaSubidaDesc(Long clienteId, Long interesadoId);

    @Query("""
            select d from Documento d
            where d.expediente.id in :expedienteIds
              and d.tipoDocumento in (
                  com.example.gestor_documental.enums.TipoDocumento.HUELLA_TRAMITE,
                  com.example.gestor_documental.enums.TipoDocumento.COMPROBANTE_DGT,
                  com.example.gestor_documental.enums.TipoDocumento.MODELO_620
              )
            """)
    List<Documento> findJustificantesFinalesByExpedienteIds(@Param("expedienteIds") List<Long> expedienteIds);

    @EntityGraph(attributePaths = {
            "expediente",
            "expediente.cliente",
            "cliente",
            "interesado",
            "solicitud",
            "solicitud.cliente"
    })
    @Query("select d from Documento d where d.id = :id")
    Optional<Documento> findByIdConRelaciones(@Param("id") Long id);


}
