package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoTramiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class SolicitudServiceImpl implements SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final TipoTramiteService tipoTramiteService;

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


    @Transactional
    @Override
    public Solicitud crearSolicitudCompleta(Solicitud solicitud, Cliente cliente, Long tipoTramiteId) {

        validarInteresadosSolicitud(solicitud);


        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId).orElseThrow();

        solicitud.setCliente(cliente);
        solicitud.setTipoTramite(tipoTramite);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);

        return solicitudRepository.save(solicitud);
    }

    private void validarInteresadosSolicitud(Solicitud solicitud) {
        validarInteresadoSolicitud(
                solicitud.getInteresado1Nombre(),
                solicitud.getInteresado1Dni(),
                solicitud.getInteresado1Rol(),
                "Interesado 1"
        );

        validarInteresadoSolicitud(
                solicitud.getInteresado2Nombre(),
                solicitud.getInteresado2Dni(),
                solicitud.getInteresado2Rol(),
                "Interesado 2"
        );

        boolean interesado1Informado = !interesadoSolicitudVacio(
                solicitud.getInteresado1Nombre(),
                solicitud.getInteresado1Dni(),
                solicitud.getInteresado1Rol()
        );

        boolean interesado2Informado = !interesadoSolicitudVacio(
                solicitud.getInteresado2Nombre(),
                solicitud.getInteresado2Dni(),
                solicitud.getInteresado2Rol()
        );

        if (interesado1Informado && interesado2Informado) {
            if (solicitud.getInteresado1Dni().equalsIgnoreCase(solicitud.getInteresado2Dni())) {
                throw new IllegalArgumentException("Los dos interesados no pueden tener el mismo DNI.");
            }
        }
    }

    private void validarInteresadoSolicitud(String nombre, String dni, RolInteresado rol, String etiqueta) {
        if (interesadoSolicitudVacio(nombre, dni, rol)) {
            return;
        }

        if (!interesadoSolicitudValido(nombre, dni, rol)) {
            throw new IllegalArgumentException(etiqueta + " está incompleto. Debe tener nombre, DNI y rol.");
        }
    }

    private boolean interesadoSolicitudVacio(String nombre, String dni, RolInteresado rol) {
        return (nombre == null || nombre.isBlank())
                && (dni == null || dni.isBlank())
                && rol == null;
    }

    private boolean interesadoSolicitudValido(String nombre, String dni, RolInteresado rol) {
        return nombre != null && !nombre.isBlank()
                && dni != null && !dni.isBlank()
                && rol != null;
    }
}

