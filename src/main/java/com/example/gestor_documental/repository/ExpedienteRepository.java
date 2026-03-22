package com.example.gestor_documental.repository;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {

    List<Expediente> findByClienteId(Long clienteId);
    List<Expediente> findByEstadoExpediente(EstadoExpediente estadoExpediente);

    int countByCliente(Cliente cliente);

    int countByClienteAndEstadoExpediente(Cliente cliente, EstadoExpediente estadoExpediente);

    int countByEstadoExpediente(EstadoExpediente estadoExpediente);
}
