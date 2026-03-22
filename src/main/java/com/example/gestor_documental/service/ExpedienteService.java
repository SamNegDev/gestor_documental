package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

public interface ExpedienteService {

    List<Expediente> listarTodos();

    Optional<Expediente> buscarPorId(Long id);

    Expediente guardar(Expediente expediente);

    void eliminarPorId(Long id);

    List<Expediente> listarPorClienteId(Long clienteId);

    boolean tienePermisoExpediente(Expediente expediente, Usuario usuario);

    int contarPorCliente(Cliente cliente);

    int contarPorClienteYEstado(Cliente cliente, EstadoExpediente estadoExpediente);

    int contarPorEstado(EstadoExpediente estadoExpediente);
}
