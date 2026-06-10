package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;

    @Override
    public List<Cliente> listarTodos() {
        return clienteRepository.findAll();
    }

    @Override
    public Optional<Cliente> buscarPorId(Long id) {
        return clienteRepository.findById(id);
    }

    @Override
    public Cliente guardar(Cliente cliente) {
        cliente.setNif(TextNormalizer.upperOrNull(cliente.getNif()));
        cliente.setNombre(TextNormalizer.upperOrNull(cliente.getNombre()));
        cliente.setEmail(TextNormalizer.lowerOrNull(cliente.getEmail()));
        cliente.setTelefono(TextNormalizer.upperOrNull(cliente.getTelefono()));
        cliente.setDireccion(TextNormalizer.upperOrNull(cliente.getDireccion()));
        return clienteRepository.save(cliente);
    }

    @Override
    public void eliminar(Long id) {
        clienteRepository.deleteById(id);
    }
}
