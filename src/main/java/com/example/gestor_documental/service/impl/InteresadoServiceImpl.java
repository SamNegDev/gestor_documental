package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.registro.InteresadoUpdateRequest;
import com.example.gestor_documental.enums.TipoPersona;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.util.DireccionFormatter;
import com.example.gestor_documental.util.NombrePersonaNormalizer;
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
        normalizarNombreEstructurado(nuevoInteresado);
        nuevoInteresado.setNombre(NombrePersonaNormalizer.normalizar(nuevoInteresado.getNombre()));
        if (nuevoInteresado.getNombre() == null) {
            nuevoInteresado.setNombre(nombreVisible(nuevoInteresado));
        }
        nuevoInteresado.setDni(TextNormalizer.upperOrNull(nuevoInteresado.getDni()));
        nuevoInteresado.setTelefono(TextNormalizer.upperOrNull(nuevoInteresado.getTelefono()));
        nuevoInteresado.setTipoVia(TextNormalizer.upperOrNull(nuevoInteresado.getTipoVia()));
        nuevoInteresado.setNombreVia(TextNormalizer.upperOrNull(nuevoInteresado.getNombreVia()));
        nuevoInteresado.setNumeroVia(TextNormalizer.upperOrNull(nuevoInteresado.getNumeroVia()));
        nuevoInteresado.setBloque(TextNormalizer.upperOrNull(nuevoInteresado.getBloque()));
        nuevoInteresado.setPortal(TextNormalizer.upperOrNull(nuevoInteresado.getPortal()));
        nuevoInteresado.setEscalera(TextNormalizer.upperOrNull(nuevoInteresado.getEscalera()));
        nuevoInteresado.setPiso(TextNormalizer.upperOrNull(nuevoInteresado.getPiso()));
        nuevoInteresado.setPuerta(TextNormalizer.upperOrNull(nuevoInteresado.getPuerta()));
        nuevoInteresado.setCodigoPostal(TextNormalizer.upperOrNull(nuevoInteresado.getCodigoPostal()));
        nuevoInteresado.setMunicipio(TextNormalizer.upperOrNull(nuevoInteresado.getMunicipio()));
        nuevoInteresado.setProvincia(TextNormalizer.upperOrNull(nuevoInteresado.getProvincia()));
        nuevoInteresado.setDireccion(TextNormalizer.upperOrNull(nuevoInteresado.getDireccion()));
        if (nuevoInteresado.getDireccion() == null) {
            nuevoInteresado.setDireccion(DireccionFormatter.componer(
                    nuevoInteresado.getTipoVia(),
                    nuevoInteresado.getNombreVia(),
                    nuevoInteresado.getNumeroVia(),
                    nuevoInteresado.getBloque(),
                    nuevoInteresado.getPortal(),
                    nuevoInteresado.getEscalera(),
                    nuevoInteresado.getPiso(),
                    nuevoInteresado.getPuerta(),
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
        String nombre = NombrePersonaNormalizer.normalizar(request.nombre());
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
        if (tieneNombreEstructurado(request)) {
            interesado.setNombrePila(TextNormalizer.upperOrNull(request.nombrePila()));
            interesado.setApellido1(TextNormalizer.upperOrNull(request.apellido1()));
            interesado.setApellido2(TextNormalizer.upperOrNull(request.apellido2()));
            interesado.setRazonSocial(NombrePersonaNormalizer.normalizar(request.razonSocial()));
            if (interesado.getRazonSocial() != null) {
                interesado.setNombre(interesado.getRazonSocial());
            } else if (nombre == null) {
                interesado.setNombre(nombreVisible(interesado));
            }
        }
        interesado.setTelefono(TextNormalizer.upperOrNull(request.telefono()));
        interesado.setTipoVia(TextNormalizer.upperOrNull(request.tipoVia()));
        interesado.setNombreVia(TextNormalizer.upperOrNull(request.nombreVia()));
        interesado.setNumeroVia(TextNormalizer.upperOrNull(request.numeroVia()));
        interesado.setBloque(TextNormalizer.upperOrNull(request.bloque()));
        interesado.setPortal(TextNormalizer.upperOrNull(request.portal()));
        interesado.setEscalera(TextNormalizer.upperOrNull(request.escalera()));
        interesado.setPiso(TextNormalizer.upperOrNull(request.piso()));
        interesado.setPuerta(TextNormalizer.upperOrNull(request.puerta()));
        interesado.setCodigoPostal(TextNormalizer.upperOrNull(request.codigoPostal()));
        interesado.setMunicipio(TextNormalizer.upperOrNull(request.municipio()));
        interesado.setProvincia(TextNormalizer.upperOrNull(request.provincia()));
        interesado.setDireccion(TextNormalizer.upperOrNull(request.direccion()));
        if (interesado.getDireccion() == null) {
            interesado.setDireccion(DireccionFormatter.componer(
                    interesado.getTipoVia(),
                    interesado.getNombreVia(),
                    interesado.getNumeroVia(),
                    interesado.getBloque(),
                    interesado.getPortal(),
                    interesado.getEscalera(),
                    interesado.getPiso(),
                    interesado.getPuerta(),
                    interesado.getCodigoPostal(),
                    interesado.getMunicipio(),
                    interesado.getProvincia()));
        }
        interesado.setTipoPersona(request.tipoPersona() != null ? request.tipoPersona() : TipoPersona.PARTICULAR);
        return interesadoRepository.save(interesado);
    }

    private boolean tieneNombreEstructurado(InteresadoUpdateRequest request) {
        return java.util.stream.Stream.of(request.nombrePila(), request.apellido1(), request.apellido2(), request.razonSocial())
                .anyMatch(value -> value != null && !value.trim().isBlank());
    }

    private void normalizarNombreEstructurado(Interesado interesado) {
        interesado.setNombrePila(TextNormalizer.upperOrNull(interesado.getNombrePila()));
        interesado.setApellido1(TextNormalizer.upperOrNull(interesado.getApellido1()));
        interesado.setApellido2(TextNormalizer.upperOrNull(interesado.getApellido2()));
        interesado.setRazonSocial(NombrePersonaNormalizer.normalizar(interesado.getRazonSocial()));
        if (interesado.getRazonSocial() != null) {
            interesado.setNombre(interesado.getRazonSocial());
        }
    }

    private String nombreVisible(Interesado interesado) {
        if (interesado.getRazonSocial() != null) {
            return interesado.getRazonSocial();
        }
        String joined = java.util.stream.Stream.of(interesado.getNombrePila(), interesado.getApellido1(), interesado.getApellido2())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        return NombrePersonaNormalizer.normalizar(joined);
    }

}
