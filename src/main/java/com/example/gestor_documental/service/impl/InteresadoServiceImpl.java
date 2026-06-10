package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor

public class InteresadoServiceImpl implements InteresadoService {

    private final InteresadoRepository interesadoRepository;

    @Override
    public Optional<Interesado> buscarInteresadoPorDNI(String dni) {
        return interesadoRepository.findByDni(TextNormalizer.upperOrNull(dni));
    }

    @Override
    public Interesado guardar(Interesado nuevoInteresado) {
        nuevoInteresado.setNombre(TextNormalizer.upperOrNull(nuevoInteresado.getNombre()));
        nuevoInteresado.setDni(TextNormalizer.upperOrNull(nuevoInteresado.getDni()));
        nuevoInteresado.setTelefono(TextNormalizer.upperOrNull(nuevoInteresado.getTelefono()));
        nuevoInteresado.setDireccion(TextNormalizer.upperOrNull(nuevoInteresado.getDireccion()));
        return interesadoRepository.save(nuevoInteresado);
    }

}
