package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    @EntityGraph(attributePaths = {"cliente", "interesado", "subidoPor"})
    List<Documento> findByExpedienteId(Long expedienteId);

    List<Documento> findByIncidenciaId(Long incidenciaId);

    List<Documento> findByExpedienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(Long expedienteId, Collection<TipoDocumento> tipos);

    @EntityGraph(attributePaths = {"cliente", "interesado", "subidoPor"})
    List<Documento> findBySolicitudId(Long solicitudId);

    @EntityGraph(attributePaths = {"expediente", "solicitud", "subidoPor"})
    @Query("""
            select d from Documento d
            where d.tipoDocumento = com.example.gestor_documental.enums.TipoDocumento.EXPEDIENTE_COMPLETO
              and d.fechaSubida >= :desde
              and d.subidoPor is not null
              and not exists (
                  select 1 from HistorialCambio historial
                  where historial.accion = 'CARGAR DOCUMENTO'
                    and historial.fechaCambio > d.fechaSubida
                    and historial.descripcion like concat('%', d.nombreArchivoOriginal, '%')
                    and (
                        (d.expediente is not null and historial.expediente = d.expediente)
                        or (d.solicitud is not null and historial.solicitud = d.solicitud)
                    )
              )
              and not exists (
                  select 1 from Documento generado
                  where generado.tipoDocumento <> com.example.gestor_documental.enums.TipoDocumento.EXPEDIENTE_COMPLETO
                    and generado.fechaSubida > d.fechaSubida
                    and (
                        (d.expediente is not null and generado.expediente = d.expediente)
                        or (d.solicitud is not null and generado.solicitud = d.solicitud)
                    )
              )
            order by d.fechaSubida asc
            """)
    List<Documento> findExpedientesCompletosPendientesDesde(@Param("desde") LocalDateTime desde, Pageable pageable);

    @EntityGraph(attributePaths = {"cliente", "interesado", "subidoPor"})
    List<Documento> findByClienteIdOrderByFechaSubidaDesc(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "interesado", "subidoPor"})
    List<Documento> findByClienteIdAndTipoDocumentoInOrderByFechaSubidaDesc(Long clienteId, Collection<TipoDocumento> tipos);

    @EntityGraph(attributePaths = {"cliente", "interesado", "subidoPor"})
    List<Documento> findByClienteIdAndInteresadoIsNullOrderByFechaSubidaDesc(Long clienteId);

    @EntityGraph(attributePaths = {"cliente", "interesado", "subidoPor"})
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

    @EntityGraph(attributePaths = {"cliente", "interesado"})
    @Query("""
            select d from Documento d
            where d.cliente is not null
              and d.interesado is not null
              and d.tipoDocumento in :tipos
              and d.fechaSubida < :limite
            order by d.fechaSubida asc
            """)
    List<Documento> findDocumentosHabitualesPendientesRevision(
            @Param("tipos") Collection<TipoDocumento> tipos,
            @Param("limite") LocalDateTime limite,
            Pageable pageable);

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
