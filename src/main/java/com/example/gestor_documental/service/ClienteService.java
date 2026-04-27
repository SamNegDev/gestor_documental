package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteService {

    List<Cliente> listarTodos();

    Optional<Cliente> buscarPorId(Long id);

    Cliente guardar(Cliente cliente);

    void eliminar(Long id);
}
