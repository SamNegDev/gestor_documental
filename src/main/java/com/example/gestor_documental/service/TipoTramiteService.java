package com.example.gestor_documental.service;

import com.example.gestor_documental.model.TipoTramite;

import java.util.List;
import java.util.Optional;

public interface TipoTramiteService {

    List<TipoTramite> listarTodos();

    Optional<TipoTramite> buscarPorId(Long id);
}
