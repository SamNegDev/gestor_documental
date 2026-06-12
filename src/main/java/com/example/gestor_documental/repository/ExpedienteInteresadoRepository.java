package com.example.gestor_documental.repository;

import com.example.gestor_documental.model.ExpedienteInteresado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpedienteInteresadoRepository extends JpaRepository<ExpedienteInteresado, Long> {

    Optional<ExpedienteInteresado> findByExpedienteIdAndInteresadoId(Long expedienteId, Long interesadoId);

    List<ExpedienteInteresado> findByExpedienteId(Long expedienteId);

    List<ExpedienteInteresado> findByInteresadoId(Long interesadoId);

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
