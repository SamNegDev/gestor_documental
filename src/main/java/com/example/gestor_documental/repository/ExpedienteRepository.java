package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRevisionGa;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {

    List<Expediente> findByClienteId(Long clienteId);

    @Query("select e from Expediente e order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc")
    List<Expediente> findAllOrderByFechaReferenciaDesc();

    @Query("select e from Expediente e where e.cliente.id = :clienteId order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc")
    List<Expediente> findByClienteIdOrderByFechaReferenciaDesc(Long clienteId);

    @Query("""
            select e from Expediente e
            where (:desde is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) >= :desde)
              and (:hasta is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) < :hasta)
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> findByPeriodo(LocalDateTime desde, LocalDateTime hasta);

    @Query("""
            select e from Expediente e
            where e.cliente.id = :clienteId
              and (:desde is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) >= :desde)
              and (:hasta is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) < :hasta)
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> findByClienteIdAndPeriodo(Long clienteId, LocalDateTime desde, LocalDateTime hasta);

    @Query(
            value = """
                    select distinct e from Expediente e
                    left join e.cliente cliente
                    left join e.tipoTramite tipoTramite
                    where (:clienteId is null or cliente.id = :clienteId)
                      and (:filtrarEstados = false or e.estadoExpediente in :estados)
                      and (:tipoTramiteId is null or tipoTramite.id = :tipoTramiteId)
                      and (:matricula is null or upper(coalesce(e.matricula, '')) like :matricula)
                      and (:desde is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) >= :desde)
                      and (:hasta is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) < :hasta)
                      and (:interesado is null or exists (
                          select 1 from ExpedienteInteresado relacion
                          join relacion.interesado interesado
                          where relacion.expediente = e
                            and (upper(coalesce(interesado.dni, '')) like :interesado
                                 or upper(coalesce(interesado.nombre, '')) like :interesado)
                      ))
                    order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
                    """,
            countQuery = """
                    select count(distinct e) from Expediente e
                    left join e.cliente cliente
                    left join e.tipoTramite tipoTramite
                    where (:clienteId is null or cliente.id = :clienteId)
                      and (:filtrarEstados = false or e.estadoExpediente in :estados)
                      and (:tipoTramiteId is null or tipoTramite.id = :tipoTramiteId)
                      and (:matricula is null or upper(coalesce(e.matricula, '')) like :matricula)
                      and (:desde is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) >= :desde)
                      and (:hasta is null or coalesce(e.fechaUltimaModificacion, e.fechaCreacion) < :hasta)
                      and (:interesado is null or exists (
                          select 1 from ExpedienteInteresado relacion
                          join relacion.interesado interesado
                          where relacion.expediente = e
                            and (upper(coalesce(interesado.dni, '')) like :interesado
                                 or upper(coalesce(interesado.nombre, '')) like :interesado)
                      ))
                    """
    )
    Page<Expediente> buscarListado(@Param("clienteId") Long clienteId,
                                   @Param("filtrarEstados") boolean filtrarEstados,
                                   @Param("estados") List<EstadoExpediente> estados,
                                   @Param("tipoTramiteId") Long tipoTramiteId,
                                   @Param("matricula") String matricula,
                                   @Param("interesado") String interesado,
                                   @Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta,
                                   Pageable pageable);

    @Query("""
            select e from Expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            where (:clienteId is null or e.cliente.id = :clienteId)
              and e.estadoExpediente in :estados
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> findTareasPorEstados(@Param("clienteId") Long clienteId,
                                          @Param("estados") List<EstadoExpediente> estados);

    @Query("""
            select e from Expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            where e.estadoExpediente = com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> findFinalizadosParaRevisionDocumental();

    @Query("""
            select e from Expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            where coalesce(e.fechaUltimaModificacion, e.fechaCreacion) < :limite
              and e.estadoExpediente not in :estadosExcluidos
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) asc
            """)
    List<Expediente> findEstancados(@Param("limite") LocalDateTime limite,
                                    @Param("estadosExcluidos") List<EstadoExpediente> estadosExcluidos);

    List<Expediente> findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();
    List<Expediente> findByEstadoExpediente(EstadoExpediente estadoExpediente);
    List<Expediente> findByExpedienteVinculadoOrigenIdAndEstadoExpediente(Long expedienteId, EstadoExpediente estadoExpediente);

    int countByCliente(Cliente cliente);

    int countByClienteAndEstadoExpediente(Cliente cliente, EstadoExpediente estadoExpediente);

    int countByEstadoExpediente(EstadoExpediente estadoExpediente);

    @Query("select e from Expediente e order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc limit 5")
    List<Expediente> findTop5OrderByFechaReferenciaDesc();

    @Query("select e from Expediente e where e.cliente = :cliente order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc limit 5")
    List<Expediente> findTop5ByClienteOrderByFechaReferenciaDesc(Cliente cliente);

    @Query("""
            select e from Expediente e
            where e.vehiculo is null
              and e.matricula is not null
              and trim(e.matricula) <> ''
            """)
    List<Expediente> findPendientesVincularVehiculo();

    @Query("""
            select distinct e from Expediente e
            left join ExpedienteInteresado ei on ei.expediente = e
            left join ei.interesado i
            where (:clienteId is null or e.cliente.id = :clienteId)
              and (upper(coalesce(e.matricula, '')) like :texto
                   or str(e.id) like :identificador
                   or upper(coalesce(i.dni, '')) like :texto
                   or upper(coalesce(i.nombre, '')) like :texto)
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> buscarGlobal(@Param("clienteId") Long clienteId,
                                  @Param("texto") String texto,
                                  @Param("identificador") String identificador,
                                  Pageable pageable);

    @Query("""
            select e from Expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            where e.cliente.id = :clienteId
              and upper(replace(replace(coalesce(e.matricula, ''), ' ', ''), '-', '')) = :matricula
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> findByClienteIdAndMatriculaNormalizada(@Param("clienteId") Long clienteId,
                                                            @Param("matricula") String matricula);

    @Query("""
            select distinct e from Expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            where exists (
                select 1 from Documento d
                where d.expediente = e
                  and d.tipoDocumento in :tiposDocumento
            )
              and not exists (
                select 1 from ExtraccionGaRevision r
                where r.expediente = e
                  and r.estado in :estadosExcluidos
            )
            order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc
            """)
    List<Expediente> findCandidatosExtraccionGa(@Param("tiposDocumento") Collection<TipoDocumento> tiposDocumento,
                                                @Param("estadosExcluidos") Collection<EstadoRevisionGa> estadosExcluidos,
                                                Pageable pageable);
}
