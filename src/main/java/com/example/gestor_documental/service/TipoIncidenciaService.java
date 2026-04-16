package com.example.gestor_documental.service;

import com.example.gestor_documental.model.TipoIncidencia;
import java.util.List;
import java.util.Optional;

public interface TipoIncidenciaService {
    List<TipoIncidencia> listarTodosActivos();
    Optional<TipoIncidencia> buscarPorId(Long id);
}
