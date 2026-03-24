package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.SolicitudService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class SolicitudServiceImpl implements SolicitudService {

    private final SolicitudRepository solicitudRepository;

    @Override
    public List<Solicitud> listarTodas() {
        return solicitudRepository.findAll();
    }

    @Override
    public Optional<Solicitud> buscarPorId(Long id) {
        return solicitudRepository.findById(id);
    }

    @Override
    public Solicitud guardar(Solicitud solicitud) {
        return solicitudRepository.save(solicitud);
    }

    @Override
    public long contarTodos() {
        return solicitudRepository.count();
    }

    @Override
    public List<Solicitud> listarPorClienteId(Long clienteId) {
        return solicitudRepository.findByClienteId(clienteId);
    }

    @Override
    public boolean tienePermisoSolicitud(Solicitud solicitud, Usuario usuario) {
        if (usuario.getRolUsuario() == RolUsuario.ADMIN) {
            return true;
        }
        //Si el usuario/solicitud no tiene cliente asignado se deniega el acceso ya que no podemos comprobar de quien es
        if (usuario.getCliente() == null || solicitud.getCliente() == null) {
            return false;
        }

        return solicitud.getCliente().getId().equals(usuario.getCliente().getId());

    }
    @Override
    public int contarPorCliente(Cliente cliente) {
        return solicitudRepository.countByCliente(cliente);
    }

    @Override
    public int contarPorClienteYEstado(Cliente cliente, EstadoSolicitud estadoSolicitud) {
        return solicitudRepository.countByClienteAndEstadoSolicitud(cliente, estadoSolicitud);
    }

    @Override
    public int contarPorEstado(EstadoSolicitud estadoSolicitud) {
        return solicitudRepository.countByEstadoSolicitud(estadoSolicitud);
    }

    @Override
    public List<Solicitud> listarUltimas() {
        return solicitudRepository.findTop5ByOrderByFechaCreacionDesc();
    }

    @Override
    public List<Solicitud> listarUltimasPorCliente(Cliente cliente) {
        return solicitudRepository.findTop5ByClienteOrderByFechaCreacionDesc(cliente);
    }
}
