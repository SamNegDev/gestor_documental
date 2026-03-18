package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.service.ExpedienteService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ExpedienteServiceImpl implements ExpedienteService {

    private final ExpedienteRepository expedienteRepository;

    public ExpedienteServiceImpl(ExpedienteRepository expedienteRepository) {
        this.expedienteRepository = expedienteRepository;
    }

    @Override
    public List<Expediente> listarTodos() {
        return expedienteRepository.findAll();
    }

    @Override
    public Optional<Expediente> buscarPorId(Long id) {
        return expedienteRepository.findById(id);
    }

    @Override
    public Expediente guardar(Expediente expediente) {
        return expedienteRepository.save(expediente);
    }

    @Override
    public void eliminarPorId(Long id) {
        expedienteRepository.deleteById(id);
    }

    @Override
    public List<Expediente> listarPorClienteId(Long clienteId) {
        return expedienteRepository.findByClienteId(clienteId);
    }
}
