package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.ExpedienteService;
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
    private final ExpedienteRepository expedienteRepository;
    private final ExpedienteService expedienteService;
    private final DocumentoRepository documentoRepository;

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
        //Si el usuarioLogueado/solicitud no tiene cliente asignado se deniega el acceso ya que no podemos comprobar de quien es
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
    public Solicitud crearSolicitudCompleta(Solicitud solicitud,Usuario usuarioLogueado, Cliente cliente, Long tipoTramiteId) {

        validarInteresadosSolicitud(solicitud);


        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId).orElseThrow();


        solicitud.setCliente(cliente);
        solicitud.setTipoTramite(tipoTramite);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        solicitud.setCreadoPor(usuarioLogueado);

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

    @Override
    @Transactional
    public Expediente convertirAExpediente(Long solicitudId, Usuario admin) {

        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA) {
            throw new RuntimeException("La solicitud ya ha sido convertida");
        }

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new RuntimeException("No se puede convertir una solicitud rechazada");
        }
        if (solicitud.getExpediente() != null) {
            throw new RuntimeException("La solicitud ya tiene un expediente asociado");
        }

        Expediente expediente = new Expediente();
        expediente.setSolicitud(solicitud);
        expediente.setCliente(solicitud.getCliente());
        expediente.setTipoTramite(solicitud.getTipoTramite());
        expediente.setMatricula(solicitud.getMatricula());
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
        expediente.setObservaciones(solicitud.getObservaciones());
        expediente.setCreadoPor(admin);

        Expediente expedienteGuardado = expedienteRepository.save(expediente);
        solicitud.setEstadoSolicitud(EstadoSolicitud.CONVERTIDA);
        asociarDocumentosSolicitudAExpediente(solicitud, expedienteGuardado);
        solicitud.setExpediente(expedienteGuardado);
        solicitudRepository.save(solicitud);


         InteresadoFormDto interesado1dto = new InteresadoFormDto();
         interesado1dto.setDni(solicitud.getInteresado1Dni());
         interesado1dto.setNombre(solicitud.getInteresado1Nombre());
         interesado1dto.setRol(solicitud.getInteresado1Rol());
         interesado1dto.setDireccion(solicitud.getInteresado1Direccion());
         interesado1dto.setTelefono(solicitud.getInteresado1Telefono());

         InteresadoFormDto interesado2dto = new InteresadoFormDto();
         interesado2dto.setDni(solicitud.getInteresado2Dni());
         interesado2dto.setNombre(solicitud.getInteresado2Nombre());
         interesado2dto.setRol(solicitud.getInteresado2Rol());
         interesado2dto.setDireccion(solicitud.getInteresado2Direccion());
         interesado2dto.setTelefono(solicitud.getInteresado2Telefono());

        expedienteService.validarInteresados(interesado1dto, interesado2dto);

        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado1dto);
        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado2dto);

        return expedienteGuardado;
    }

    @Override
    @Transactional
    public void cambiarEstadoSolicitud(Long id, EstadoSolicitud nuevoEstado, Usuario usuarioLogueado) {


        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        if (!tienePermisoSolicitud(solicitud, usuarioLogueado)){
            throw new AccesoDenegadoException("No tienes permiso para acceder a esta solicitud");
        }
        if (usuarioLogueado.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede cambiar el estado de la solicitud");
        }
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede cambiar el estado de una solicitud rechazada");
        }

        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA) {
            throw new OperacionInvalidaException("No se puede cambiar el estado de una solicitud convertida");
        }

        if (solicitud.getExpediente() != null) {
            throw new OperacionInvalidaException("La solicitud ya tiene un expediente asociado");
        }

        solicitud.setEstadoSolicitud(nuevoEstado);
        solicitudRepository.save(solicitud);

    }

    public void asociarDocumentosSolicitudAExpediente(Solicitud solicitud, Expediente expediente) {
        List<Documento> documentos = documentoRepository.findBySolicitudId(solicitud.getId());

        if (documentos == null || documentos.isEmpty()) {
            return;
        }

        for (Documento documento : documentos) {
            documento.setExpediente(expediente);
        }

        documentoRepository.saveAll(documentos);
    }

    @Override
    @Transactional
    public Solicitud actualizarSolicitud(Long id, Solicitud solicitudActualizada, Usuario usuarioLogueado, Long tipoTramiteId) {

        Solicitud solicitudBase = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        if (!tienePermisoSolicitud(solicitudBase, usuarioLogueado)){
            throw new AccesoDenegadoException("No tienes permiso para acceder a esta solicitud");
        }

        if (solicitudBase.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA ||
            solicitudBase.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede editar una solicitud convertida o rechazada");
        }

        validarInteresadosSolicitud(solicitudActualizada);

        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId).orElseThrow(() -> new RecursoNoEncontradoException("Tipo de trámite no encontrado"));

        solicitudBase.setTipoTramite(tipoTramite);
        solicitudBase.setMatricula(solicitudActualizada.getMatricula());

        solicitudBase.setInteresado1Rol(solicitudActualizada.getInteresado1Rol());
        solicitudBase.setInteresado1Nombre(solicitudActualizada.getInteresado1Nombre());
        solicitudBase.setInteresado1Apellidos(solicitudActualizada.getInteresado1Apellidos());
        solicitudBase.setInteresado1Dni(solicitudActualizada.getInteresado1Dni());
        solicitudBase.setInteresado1Telefono(solicitudActualizada.getInteresado1Telefono());
        solicitudBase.setInteresado1Direccion(solicitudActualizada.getInteresado1Direccion());

        solicitudBase.setInteresado2Rol(solicitudActualizada.getInteresado2Rol());
        solicitudBase.setInteresado2Nombre(solicitudActualizada.getInteresado2Nombre());
        solicitudBase.setInteresado2Apellidos(solicitudActualizada.getInteresado2Apellidos());
        solicitudBase.setInteresado2Dni(solicitudActualizada.getInteresado2Dni());
        solicitudBase.setInteresado2Telefono(solicitudActualizada.getInteresado2Telefono());
        solicitudBase.setInteresado2Direccion(solicitudActualizada.getInteresado2Direccion());

        solicitudBase.setObservaciones(solicitudActualizada.getObservaciones());

        return solicitudRepository.save(solicitudBase);
    }
}

