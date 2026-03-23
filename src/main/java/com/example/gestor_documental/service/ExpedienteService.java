package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public interface ExpedienteService {

    List<Expediente> listarTodos();

    Optional<Expediente> buscarPorId(Long id);

    Expediente guardar(Expediente expediente);

    void eliminarPorId(Long id);

    long contarTodos();

    List<Expediente> listarPorClienteId(Long clienteId);

    boolean tienePermisoExpediente(Expediente expediente, Usuario usuario);

    int contarPorCliente(Cliente cliente);

    int contarPorClienteYEstado(Cliente cliente, EstadoExpediente estadoExpediente);

    int contarPorEstado(EstadoExpediente estadoExpediente);

    List<Expediente> listarUltimos();

    List<Expediente> listarUltimosPorCliente(Cliente cliente);


}
