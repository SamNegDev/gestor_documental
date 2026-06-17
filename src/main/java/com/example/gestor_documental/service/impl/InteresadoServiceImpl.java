package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.registro.InteresadoUpdateRequest;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.util.DireccionFormatter;
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
        nuevoInteresado.setTipoVia(TextNormalizer.upperOrNull(nuevoInteresado.getTipoVia()));
        nuevoInteresado.setNombreVia(TextNormalizer.upperOrNull(nuevoInteresado.getNombreVia()));
        nuevoInteresado.setCodigoPostal(TextNormalizer.upperOrNull(nuevoInteresado.getCodigoPostal()));
        nuevoInteresado.setMunicipio(TextNormalizer.upperOrNull(nuevoInteresado.getMunicipio()));
        nuevoInteresado.setProvincia(TextNormalizer.upperOrNull(nuevoInteresado.getProvincia()));
        nuevoInteresado.setDireccion(TextNormalizer.upperOrNull(nuevoInteresado.getDireccion()));
        if (nuevoInteresado.getDireccion() == null) {
            nuevoInteresado.setDireccion(DireccionFormatter.componer(
                    nuevoInteresado.getTipoVia(),
                    nuevoInteresado.getNombreVia(),
                    nuevoInteresado.getCodigoPostal(),
                    nuevoInteresado.getMunicipio(),
                    nuevoInteresado.getProvincia()));
        }
        return interesadoRepository.save(nuevoInteresado);
    }

    @Override
    public Interesado actualizar(Long id, InteresadoUpdateRequest request) {
        Interesado interesado = interesadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"));
        String dni = TextNormalizer.upperOrNull(request.dni());
        String nombre = TextNormalizer.upperOrNull(request.nombre());
        if (dni == null || nombre == null) {
            throw new OperacionInvalidaException("DNI y nombre son obligatorios");
        }
        interesadoRepository.findByDni(dni)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new OperacionInvalidaException("Ya existe otro interesado con ese DNI");
                });
        interesado.setDni(dni);
        interesado.setNombre(nombre);
        interesado.setTelefono(TextNormalizer.upperOrNull(request.telefono()));
        interesado.setTipoVia(TextNormalizer.upperOrNull(request.tipoVia()));
        interesado.setNombreVia(TextNormalizer.upperOrNull(request.nombreVia()));
        interesado.setCodigoPostal(TextNormalizer.upperOrNull(request.codigoPostal()));
        interesado.setMunicipio(TextNormalizer.upperOrNull(request.municipio()));
        interesado.setProvincia(TextNormalizer.upperOrNull(request.provincia()));
        interesado.setDireccion(TextNormalizer.upperOrNull(request.direccion()));
        if (interesado.getDireccion() == null) {
            interesado.setDireccion(DireccionFormatter.componer(
                    interesado.getTipoVia(),
                    interesado.getNombreVia(),
                    interesado.getCodigoPostal(),
                    interesado.getMunicipio(),
                    interesado.getProvincia()));
        }
        interesado.setTipoPersona(request.tipoPersona() != null ? request.tipoPersona() : TipoPersona.PARTICULAR);
        return interesadoRepository.save(interesado);
    }

}
