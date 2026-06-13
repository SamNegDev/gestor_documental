package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

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

    List<Expediente> findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();
    List<Expediente> findByEstadoExpediente(EstadoExpediente estadoExpediente);

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
}
