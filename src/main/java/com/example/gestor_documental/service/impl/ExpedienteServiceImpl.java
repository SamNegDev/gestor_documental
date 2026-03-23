package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
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
    public long contarTodos() {
        return expedienteRepository.count();
    }

    @Override
    public List<Expediente> listarPorClienteId(Long clienteId) {
        return expedienteRepository.findByClienteId(clienteId);
    }

    @Override
    public boolean tienePermisoExpediente(Expediente expediente, Usuario usuario) {

        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            return true;
        }
        //Si el usuario/expediente no tiene cliente asignado se deniega el acceso ya que no podemos comprobar de quien es
        if (usuario.getCliente() == null || expediente.getCliente() == null) {
            return false;
        }

        return expediente.getCliente().getId().equals(usuario.getCliente().getId());
    }

    @Override
    public int contarPorCliente(Cliente cliente) {
        return expedienteRepository.countByCliente(cliente);
    }

    @Override
    public int contarPorClienteYEstado(Cliente cliente, EstadoExpediente estadoExpediente) {
        return expedienteRepository.countByClienteAndEstadoExpediente(cliente, estadoExpediente);
    }

    @Override
    public int contarPorEstado(EstadoExpediente estadoExpediente) {
        return expedienteRepository.countByEstadoExpediente(estadoExpediente);
    }

    @Override
    public List<Expediente> listarUltimos() {
        return expedienteRepository.findTop5ByOrderByFechaCreacionDesc();
    }

    @Override
    public List<Expediente> listarUltimosPorCliente(Cliente cliente) {
        return expedienteRepository.findTop5ByClienteOrderByFechaCreacionDesc(cliente);
    }

}
