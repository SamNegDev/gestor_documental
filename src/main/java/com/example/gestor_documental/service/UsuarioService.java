package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioService {

    List<Usuario> listarTodos();

    Optional<Usuario> buscarPorId(Long id);

    Usuario buscarPorEmail(String email);

    Usuario guardar(Usuario usuario);

    void eliminarPorId(Long id);
}