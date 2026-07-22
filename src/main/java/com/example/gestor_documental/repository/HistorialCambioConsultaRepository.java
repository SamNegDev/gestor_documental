package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.AudienciaHistorial;
import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.TipoActividadHistorial;
import com.example.gestor_documental.model.HistorialCambio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HistorialCambioConsultaRepository extends Repository<HistorialCambio, Long> {

    @Query("""
            select h from HistorialCambio h
            left join fetch h.usuario
            where h.expediente.id = :expedienteId
              and (
                :categoria is null
                or h.categoria = :categoria
                or (h.categoria is null and (
                  (:categoria = com.example.gestor_documental.enums.CategoriaHistorial.COMUNICACION
                    and (h.tipoActividad = :comunicacion or upper(h.accion) in :accionesComunicacion))
                  or (:categoria = com.example.gestor_documental.enums.CategoriaHistorial.DOCUMENTO
                    and upper(h.accion) like '%DOCUMENT%')
                  or (:categoria = com.example.gestor_documental.enums.CategoriaHistorial.INCIDENCIA
                    and upper(h.accion) like '%INCIDENCIA%')
                  or (:categoria = com.example.gestor_documental.enums.CategoriaHistorial.ESTADO
                    and (upper(h.accion) like '%ESTADO%' or upper(h.accion) like '%FINALIZ%'))
                  or (:categoria = com.example.gestor_documental.enums.CategoriaHistorial.TRAMITE
                    and (upper(h.accion) like '%HITO%' or upper(h.accion) like '%TRAMITE%'))
                  or (:categoria = com.example.gestor_documental.enums.CategoriaHistorial.SISTEMA
                    and upper(h.accion) not like '%DOCUMENT%' and upper(h.accion) not like '%INCIDENCIA%'
                    and upper(h.accion) not like '%ESTADO%' and upper(h.accion) not like '%FINALIZ%'
                    and upper(h.accion) not like '%HITO%' and upper(h.accion) not like '%TRAMITE%')
                ))
              )
              and (
                :soloCliente = false
                or h.audiencia in :audienciasCliente
                or (h.audiencia is null and (
                  h.tipoActividad = :comunicacion or upper(h.accion) in :accionesComunicacion
                  or upper(h.accion) like '%ESTADO%' or upper(h.accion) like '%FINALIZ%'
                  or upper(h.accion) like '%INCIDENCIA%'
                  or (upper(h.accion) like '%DOCUMENT%' and upper(h.accion) not like '%ELIMIN%'
                    and upper(h.accion) not like '%DESCARG%' and upper(h.accion) not like '%CONSULT%')
                ))
              )
            """)
    Page<HistorialCambio> buscar(@Param("expedienteId") Long expedienteId,
                                 @Param("categoria") CategoriaHistorial categoria,
                                 @Param("soloCliente") boolean soloCliente,
                                 @Param("audienciasCliente") List<AudienciaHistorial> audienciasCliente,
                                 @Param("comunicacion") TipoActividadHistorial comunicacion,
                                 @Param("accionesComunicacion") List<String> accionesComunicacion,
                                 Pageable pageable);
}
