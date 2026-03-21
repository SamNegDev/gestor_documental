package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface ExpedienteService {

    List<Expediente> listarTodos();

    Optional<Expediente> buscarPorId(Long id);

    Expediente guardar(Expediente expediente);

    void eliminarPorId(Long id);

    List<Expediente> listarPorClienteId(Long clienteId);

    boolean tienePermisoExpediente(Expediente expediente, Usuario usuario);
}
