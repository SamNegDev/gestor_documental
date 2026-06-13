package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.registro.VehiculoUpdateRequest;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Vehiculo;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.VehiculoRepository;
import com.example.gestor_documental.service.VehiculoService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VehiculoServiceImpl implements VehiculoService {

    private final VehiculoRepository vehiculoRepository;
    private final ExpedienteRepository expedienteRepository;

    @Override
    @Transactional
    public Vehiculo obtenerOCrearPorMatricula(String matricula) {
        String normalizada = normalizarMatricula(matricula);
        if (normalizada == null) {
            return null;
        }
        return vehiculoRepository.findByMatricula(normalizada)
                .orElseGet(() -> {
                    Vehiculo vehiculo = new Vehiculo();
                    vehiculo.setMatricula(normalizada);
                    return vehiculoRepository.save(vehiculo);
                });
    }

    @Override
    @Transactional
    public Vehiculo actualizarPorMatricula(String matricula, VehiculoUpdateRequest request) {
        String normalizada = normalizarMatricula(matricula);
        if (normalizada == null) {
            throw new RecursoNoEncontradoException("Vehiculo no encontrado");
        }
        Vehiculo vehiculo = vehiculoRepository.findByMatricula(normalizada)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehiculo no encontrado"));
        vehiculo.setBastidor(TextNormalizer.upperOrNull(request.bastidor()));
        vehiculo.setMarca(TextNormalizer.upperOrNull(request.marca()));
        vehiculo.setModelo(TextNormalizer.upperOrNull(request.modelo()));
        vehiculo.setFechaPrimeraMatriculacion(request.fechaPrimeraMatriculacion());
        vehiculo.setObservaciones(TextNormalizer.upperOrNull(request.observaciones()));
        return vehiculoRepository.save(vehiculo);
    }

    @Override
    @Transactional
    public int migrarExpedientesExistentes() {
        int actualizados = 0;
        for (Expediente expediente : expedienteRepository.findPendientesVincularVehiculo()) {
            Vehiculo vehiculo = obtenerOCrearPorMatricula(expediente.getMatricula());
            if (vehiculo != null) {
                expediente.setVehiculo(vehiculo);
                expedienteRepository.save(expediente);
                actualizados++;
            }
        }
        return actualizados;
    }

    private String normalizarMatricula(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
