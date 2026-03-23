package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.repository.TipoTramiteRepository;
import com.example.gestor_documental.service.TipoTramiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TipoTramiteServiceImpl implements TipoTramiteService {

    private final TipoTramiteRepository tipoTramiteRepository;

    @Override
    public List<TipoTramite> listarTodos() {
        return tipoTramiteRepository.findAll();
    }

    @Override
    public Optional<TipoTramite> buscarPorId(Long id) {
        return tipoTramiteRepository.findById(id);
    }
}
