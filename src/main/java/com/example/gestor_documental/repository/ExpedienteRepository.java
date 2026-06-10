package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {

    List<Expediente> findByClienteId(Long clienteId);

    @Query("select e from Expediente e order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc")
    List<Expediente> findAllOrderByFechaReferenciaDesc();

    @Query("select e from Expediente e where e.cliente.id = :clienteId order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc")
    List<Expediente> findByClienteIdOrderByFechaReferenciaDesc(Long clienteId);

    List<Expediente> findByFechaUltimaModificacionIsNullOrModificadoPorIsNull();
    List<Expediente> findByEstadoExpediente(EstadoExpediente estadoExpediente);

    int countByCliente(Cliente cliente);

    int countByClienteAndEstadoExpediente(Cliente cliente, EstadoExpediente estadoExpediente);

    int countByEstadoExpediente(EstadoExpediente estadoExpediente);

    @Query("select e from Expediente e order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc limit 5")
    List<Expediente> findTop5OrderByFechaReferenciaDesc();

    @Query("select e from Expediente e where e.cliente = :cliente order by coalesce(e.fechaUltimaModificacion, e.fechaCreacion) desc limit 5")
    List<Expediente> findTop5ByClienteOrderByFechaReferenciaDesc(Cliente cliente);
}
