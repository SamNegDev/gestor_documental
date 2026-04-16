package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.TipoIncidencia;
import com.example.gestor_documental.repository.TipoIncidenciaRepository;
import com.example.gestor_documental.service.TipoIncidenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TipoIncidenciaServiceImpl implements TipoIncidenciaService {

    private final TipoIncidenciaRepository tipoIncidenciaRepository;

    @Override
    public List<TipoIncidencia> listarTodosActivos() {
        return tipoIncidenciaRepository.findAll().stream()
                .filter(TipoIncidencia::isActivo)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TipoIncidencia> buscarPorId(Long id) {
        return tipoIncidenciaRepository.findById(id);
    }
}
