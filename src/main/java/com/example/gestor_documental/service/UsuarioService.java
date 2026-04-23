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

    Usuario crearUsuario(Usuario usuario, Long clienteId, String rawPassword);

    Usuario actualizarUsuario(Long id, Usuario datosNuevos, Long clienteId, String newRawPassword);

    void eliminarUsuarioSeguro(Long id);
}