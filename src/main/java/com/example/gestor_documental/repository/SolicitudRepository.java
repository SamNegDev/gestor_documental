package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    List<Solicitud> findByClienteId(Long clienteId);

    @Query("select s from Solicitud s order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc")
    List<Solicitud> findAllOrderByFechaReferenciaDesc();

    @Query("select s from Solicitud s where s.cliente.id = :clienteId order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc")
    List<Solicitud> findByClienteIdOrderByFechaReferenciaDesc(Long clienteId);

    @Query("""
            select s from Solicitud s
            left join fetch s.cliente
            left join fetch s.tipoTramite
            where s.cliente.id = :clienteId
              and upper(replace(replace(coalesce(s.matricula, ''), ' ', ''), '-', '')) = :matricula
            order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc
            """)
    List<Solicitud> findByClienteIdAndMatriculaNormalizada(@Param("clienteId") Long clienteId,
                                                           @Param("matricula") String matricula);

    @Query(
            value = """
                    select s from Solicitud s
                    left join s.cliente cliente
                    left join s.tipoTramite tipoTramite
                    where (:clienteId is null or cliente.id = :clienteId)
                      and (:estado is null or s.estadoSolicitud = :estado)
                      and (:estado is not null
                           or :archivo = 'TODAS'
                           or (:archivo = 'ARCHIVADAS' and s.estadoSolicitud in (
                                com.example.gestor_documental.enums.EstadoSolicitud.CONVERTIDA,
                                com.example.gestor_documental.enums.EstadoSolicitud.RECHAZADO
                           ))
                           or (:archivo = 'ACTIVAS' and s.estadoSolicitud not in (
                                com.example.gestor_documental.enums.EstadoSolicitud.CONVERTIDA,
                                com.example.gestor_documental.enums.EstadoSolicitud.RECHAZADO
                           )))
                      and (:tipoTramiteId is null or tipoTramite.id = :tipoTramiteId)
                      and (:matricula is null or upper(coalesce(s.matricula, '')) like :matricula)
                      and (:desde is null or coalesce(s.fechaUltimaModificacion, s.fechaCreacion) >= :desde)
                      and (:hasta is null or coalesce(s.fechaUltimaModificacion, s.fechaCreacion) < :hasta)
                    order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc
                    """,
            countQuery = """
                    select count(s) from Solicitud s
                    left join s.cliente cliente
                    left join s.tipoTramite tipoTramite
                    where (:clienteId is null or cliente.id = :clienteId)
                      and (:estado is null or s.estadoSolicitud = :estado)
                      and (:estado is not null
                           or :archivo = 'TODAS'
                           or (:archivo = 'ARCHIVADAS' and s.estadoSolicitud in (
                                com.example.gestor_documental.enums.EstadoSolicitud.CONVERTIDA,
                                com.example.gestor_documental.enums.EstadoSolicitud.RECHAZADO
                           ))
                           or (:archivo = 'ACTIVAS' and s.estadoSolicitud not in (
                                com.example.gestor_documental.enums.EstadoSolicitud.CONVERTIDA,
                                com.example.gestor_documental.enums.EstadoSolicitud.RECHAZADO
                           )))
                      and (:tipoTramiteId is null or tipoTramite.id = :tipoTramiteId)
                      and (:matricula is null or upper(coalesce(s.matricula, '')) like :matricula)
                      and (:desde is null or coalesce(s.fechaUltimaModificacion, s.fechaCreacion) >= :desde)
                      and (:hasta is null or coalesce(s.fechaUltimaModificacion, s.fechaCreacion) < :hasta)
                    """
    )
    Page<Solicitud> buscarListado(@Param("clienteId") Long clienteId,
                                  @Param("estado") EstadoSolicitud estado,
                                  @Param("archivo") String archivo,
                                  @Param("tipoTramiteId") Long tipoTramiteId,
                                  @Param("matricula") String matricula,
                                  @Param("desde") LocalDateTime desde,
                                  @Param("hasta") LocalDateTime hasta,
                                  Pageable pageable);

    @Query("""
            select s from Solicitud s
            left join fetch s.cliente
            left join fetch s.tipoTramite
            where (:clienteId is null or s.cliente.id = :clienteId)
              and s.estadoSolicitud in :estados
            order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc
            """)
    List<Solicitud> findTareasPendientes(@Param("clienteId") Long clienteId,
                                         @Param("estados") List<EstadoSolicitud> estados);

    List<Solicitud> findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();

    int countByCliente(Cliente cliente);

    int countByClienteAndEstadoSolicitud(Cliente cliente, EstadoSolicitud estadoSolicitud);

    int countByEstadoSolicitud(EstadoSolicitud estadoSolicitud);

    @Query("select s from Solicitud s order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc limit 5")
    List<Solicitud> findTop5OrderByFechaReferenciaDesc();

    @Query("select s from Solicitud s where s.cliente = :cliente order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc limit 5")
    List<Solicitud> findTop5ByClienteOrderByFechaReferenciaDesc(Cliente cliente);

    @Query("""
            select s from Solicitud s
            where (:clienteId is null or s.cliente.id = :clienteId)
              and (upper(coalesce(s.matricula, '')) like :texto
                   or str(s.id) like :identificador
                   or upper(coalesce(s.interesado1Dni, '')) like :texto
                   or upper(coalesce(s.interesado1Nombre, '')) like :texto
                   or upper(coalesce(s.interesado2Dni, '')) like :texto
                   or upper(coalesce(s.interesado2Nombre, '')) like :texto
                   or upper(coalesce(s.interesado3Dni, '')) like :texto
                   or upper(coalesce(s.interesado3Nombre, '')) like :texto)
            order by coalesce(s.fechaUltimaModificacion, s.fechaCreacion) desc
            """)
    List<Solicitud> buscarGlobal(@Param("clienteId") Long clienteId,
                                 @Param("texto") String texto,
                                 @Param("identificador") String identificador,
                                 Pageable pageable);
}
