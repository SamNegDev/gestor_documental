package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.ExpedienteInteresado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpedienteInteresadoRepository extends JpaRepository<ExpedienteInteresado, Long> {

    Optional<ExpedienteInteresado> findByExpedienteIdAndInteresadoId(Long expedienteId, Long interesadoId);

    List<ExpedienteInteresado> findByExpedienteId(Long expedienteId);

    @Query("""
            select relacion
            from ExpedienteInteresado relacion
            join fetch relacion.expediente expediente
            join fetch relacion.interesado interesado
            left join fetch expediente.cliente cliente
            left join fetch expediente.tipoTramite
            where (:clienteId is null or cliente.id = :clienteId)
              and (:desde is null or coalesce(expediente.fechaUltimaModificacion, expediente.fechaCreacion) >= :desde)
              and (:hasta is null or coalesce(expediente.fechaUltimaModificacion, expediente.fechaCreacion) < :hasta)
            """)
    List<ExpedienteInteresado> findRegistro(
            @Param("clienteId") Long clienteId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta
    );

    @Query("""
            select relacion
            from ExpedienteInteresado relacion
            join fetch relacion.expediente expediente
            join fetch relacion.interesado interesado
            left join fetch expediente.cliente cliente
            left join fetch expediente.tipoTramite
            where interesado.id = :interesadoId
              and (:clienteId is null or cliente.id = :clienteId)
            """)
    List<ExpedienteInteresado> findRegistroByInteresadoId(
            @Param("interesadoId") Long interesadoId,
            @Param("clienteId") Long clienteId
    );

    @Query("""
            select relacion
            from ExpedienteInteresado relacion
            join fetch relacion.expediente expediente
            join fetch relacion.interesado interesado
            where expediente.cliente.id = :clienteId
            """)
    List<ExpedienteInteresado> findByClienteId(@Param("clienteId") Long clienteId);

    @Query("""
            select distinct relacion.expediente.id
            from ExpedienteInteresado relacion
            join relacion.interesado interesado
            where lower(interesado.dni) like lower(concat('%', :texto, '%'))
               or lower(interesado.nombre) like lower(concat('%', :texto, '%'))
            """)
    List<Long> buscarExpedienteIdsPorInteresado(@Param("texto") String texto);

    void deleteByExpedienteId(Long expedienteId);
}
