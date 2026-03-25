package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.InteresadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor

public class InteresadoServiceImpl implements InteresadoService {

    private final InteresadoRepository interesadoRepository;

    @Override
    public Optional<Interesado> buscarInteresadoPorDNI(String dni) {
        return interesadoRepository.findByDni(dni);
    }

    @Override
    public Interesado guardar(Interesado nuevoInteresado) {
        return interesadoRepository.save(nuevoInteresado);
    }

}
