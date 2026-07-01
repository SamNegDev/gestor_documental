package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoExtraccionGaJob;
import com.example.gestor_documental.model.ExtraccionGaJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExtraccionGaJobRepository extends JpaRepository<ExtraccionGaJob, Long> {

    @EntityGraph(attributePaths = {"expediente", "expediente.cliente", "expediente.tipoTramite", "revision"})
    Optional<ExtraccionGaJob> findTopByExpedienteIdOrderByFechaCreacionDesc(Long expedienteId);

    List<ExtraccionGaJob> findByEstadoInOrderByFechaCreacionAsc(Collection<EstadoExtraccionGaJob> estados, Pageable pageable);

    @Query("""
            select count(j) from ExtraccionGaJob j
            where j.expediente.id = :expedienteId
              and j.usoCliente = true
            """)
    long countUsosClienteByExpedienteId(@Param("expedienteId") Long expedienteId);

    @Query("""
            select j from ExtraccionGaJob j
            join fetch j.expediente e
            left join fetch e.cliente
            left join fetch e.tipoTramite
            left join fetch j.revision
            where j.expediente.id in :expedienteIds
              and j.fechaCreacion = (
                  select max(j2.fechaCreacion)
                  from ExtraccionGaJob j2
                  where j2.expediente.id = j.expediente.id
              )
            """)
    List<ExtraccionGaJob> findUltimosPorExpediente(@Param("expedienteIds") Collection<Long> expedienteIds);
}
