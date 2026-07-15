package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.Incidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {
    @Query("select i from Incidencia i left join fetch i.expediente e left join fetch e.cliente left join fetch i.tipoIncidencia")
    List<Incidencia> findAllWithDetails();

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
              and (i.contadorAvisos = 0 or (i.proximoAviso is not null and i.proximoAviso <= :ahora))
            """)
    List<Incidencia> findSeguimientoPendiente(@Param("ahora") LocalDateTime ahora);

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and c.id = :clienteId
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
              and (i.contadorAvisos = 0 or (i.proximoAviso is not null and i.proximoAviso <= :ahora))
            order by i.fechaCreacion asc
            """)
    List<Incidencia> findSeguimientoPendienteByCliente(@Param("clienteId") Long clienteId,
                                                       @Param("ahora") LocalDateTime ahora);

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and i.contadorAvisos = 0
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by c.nombre asc, i.fechaCreacion asc
            """)
    List<Incidencia> findPendientesPrimerAviso();

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and c.id = :clienteId
              and i.contadorAvisos = 0
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by i.fechaCreacion asc
            """)
    List<Incidencia> findPendientesPrimerAvisoByCliente(@Param("clienteId") Long clienteId);

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and i.contadorAvisos > 0
              and i.proximoAviso is not null
              and i.proximoAviso <= :ahora
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by c.nombre asc, i.proximoAviso asc
            """)
    List<Incidencia> findRecordatoriosPendientes(@Param("ahora") LocalDateTime ahora);

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and c.id = :clienteId
              and i.contadorAvisos > 0
              and i.proximoAviso is not null
              and i.proximoAviso <= :ahora
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by i.proximoAviso asc
            """)
    List<Incidencia> findRecordatoriosPendientesByCliente(@Param("clienteId") Long clienteId,
                                                          @Param("ahora") LocalDateTime ahora);

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch e.tipoTramite
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by c.nombre asc, i.fechaCreacion asc
            """)
    List<Incidencia> findActivasResumen();

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch e.tipoTramite
            left join fetch i.tipoIncidencia
            where i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and c.id = :clienteId
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by i.fechaCreacion asc
            """)
    List<Incidencia> findActivasResumenByCliente(@Param("clienteId") Long clienteId);

    @Query("""
            select i from Incidencia i
            left join fetch i.expediente e
            left join fetch e.cliente c
            left join fetch e.tipoTramite
            left join fetch i.tipoIncidencia
            where i.id in :ids
              and i.resuelta = false
              and i.seguimientoArchivado = false
              and i.expediente is not null
              and e.estadoExpediente not in (com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO, com.example.gestor_documental.enums.EstadoExpediente.RECHAZADO)
            order by i.fechaCreacion asc
            """)
    List<Incidencia> findActivasResumenByIds(@Param("ids") List<Long> ids);

    List<Incidencia> findByExpedienteId(Long expedienteId);
    List<Incidencia> findBySolicitudId(Long solicitudId);
    List<Incidencia> findByExpedienteIdAndResueltaFalse(Long expedienteId);
    List<Incidencia> findBySolicitudIdAndResueltaFalse(Long solicitudId);
}
