package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface SolicitudService {

    List<Solicitud> listarTodas();

    Optional<Solicitud> buscarPorId(Long id);

    Solicitud guardar(Solicitud solicitud);

    long contarTodos();

    List<Solicitud> listarPorClienteId(Long clienteId);

    boolean tienePermisoSolicitud(Solicitud solicitud, Usuario usuario);

    int contarPorCliente(Cliente cliente);

    int contarPorClienteYEstado(Cliente cliente, EstadoSolicitud estadoSolicitud);

    int contarPorEstado(EstadoSolicitud estadoSolicitud);

    List<Solicitud> listarUltimas();

    List<Solicitud> listarUltimasPorCliente(Cliente cliente);

    Solicitud crearSolicitudCompleta(Solicitud solicitud, Usuario usuarioLogueado,
            Cliente cliente,
            Long tipoTramiteId);

    Expediente convertirAExpediente(Long solicitudId, Usuario admin);

    void cambiarEstadoSolicitud(Long id, EstadoSolicitud nuevoEstado, Usuario admin);
}
