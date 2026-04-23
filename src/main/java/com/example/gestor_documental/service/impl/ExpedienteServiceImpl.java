package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.service.TipoTramiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpedienteServiceImpl implements ExpedienteService {

    private final ExpedienteRepository expedienteRepository;
    private final InteresadoService interesadoService;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;
    private final IncidenciaRepository incidenciaRepository;
    private final HistorialCambioService historialCambioService;

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
        // Si el usuarioLogueado/expediente no tiene cliente asignado se deniega el
        // acceso ya que no podemos comprobar de quien es
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

    @Override
    public void guardarInteresados(Expediente expediente,
            InteresadoFormDto interesado1,
            InteresadoFormDto interesado2) {

        guardarInteresadoSiValido(expediente, interesado1);
        guardarInteresadoSiValido(expediente, interesado2);
    }

    public void guardarInteresadoSiValido(Expediente expediente, InteresadoFormDto dto) {

        if (interesadoVacio(dto)) {
            return;
        }

        Interesado interesado = interesadoService.buscarInteresadoPorDNI(dto.getDni())
                .orElseGet(() -> {
                    Interesado nuevoInteresado = new Interesado();
                    nuevoInteresado.setNombre(dto.getNombre());
                    nuevoInteresado.setDni(dto.getDni());
                    nuevoInteresado.setTelefono(dto.getTelefono());
                    nuevoInteresado.setDireccion(dto.getDireccion());
                    return interesadoService.guardar(nuevoInteresado);
                });

        boolean yaRelacionado = expedienteInteresadoRepository
                .findByExpedienteIdAndInteresadoId(expediente.getId(), interesado.getId())
                .isPresent();

        if (yaRelacionado) {
            return;
        }

        ExpedienteInteresado relacion = new ExpedienteInteresado();
        relacion.setExpediente(expediente);
        relacion.setInteresado(interesado);
        relacion.setRol(dto.getRol());

        expedienteInteresadoRepository.save(relacion);
    }

    /**
     * Modifica el estado de un expediente aplicando bloqueos de seguridad y negocio.
     * Reglas clave:
     * - Solo un usuario administrador puede alterar los estados.
     * - No se puede alterar el estado de un expediente que ya se marcó como FINALIZADO.
     * - Impide la transición a EN_TRAMITE o FINALIZADO si el expediente posee 
     *   incidencias activas que no han sido resueltas previamente.
     */
    @Override
    @Transactional
    public void cambiarEstado(Long id, EstadoExpediente nuevoEstado, Usuario usuarioLogueado) {

        Expediente expediente = expedienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        if (!tienePermisoExpediente(expediente, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }
        if (usuarioLogueado.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede cambiar el estado del expediente.");
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("No se puede modificar un expediente finalizado");
        }

        if (nuevoEstado == EstadoExpediente.EN_TRAMITE || nuevoEstado == EstadoExpediente.FINALIZADO) {

            long incidenciasActivas = incidenciaRepository.findByExpedienteIdAndResueltaFalse(expediente.getId())
                    .size();
            if (incidenciasActivas > 0) {
                throw new OperacionInvalidaException(
                        "No se puede poner en trámite o finalizar un expediente con incidencias activas sin resolver.");
            }
        }

        EstadoExpediente estadoAnterior = expediente.getEstadoExpediente();

        if (estadoAnterior == nuevoEstado) {
            return;
        }

        expediente.setEstadoExpediente(nuevoEstado);

        expedienteRepository.save(expediente);

        historialCambioService.registrarCambioExpediente(
                expediente,
                usuarioLogueado,
                "CAMBIO ESTADO",
                "El estado cambió de '" + estadoAnterior.name() + "' a '" + nuevoEstado.name() + "'");
    }

    private boolean interesadoValido(InteresadoFormDto dto) {
        return dto != null
                && dto.getNombre() != null && !dto.getNombre().isBlank()
                && dto.getDni() != null && !dto.getDni().isBlank()
                && dto.getRol() != null;
    }

    private boolean interesadoVacio(InteresadoFormDto dto) {
        return dto == null
                || ((dto.getNombre() == null || dto.getNombre().isBlank())
                        && (dto.getDni() == null || dto.getDni().isBlank())
                        && dto.getRol() == null);
    }

    /**
     * Valida que, si un interesado tiene algún dato rellenado, estén obligatoriamente todos 
     * los datos esenciales completados (Nombre, DNI y Rol). Evita la creación de interesados "a medias".
     */
    private void validarInteresado(InteresadoFormDto dto, String nombreInteresado) {
        if (interesadoVacio(dto)) {
            return;
        }

        if (!interesadoValido(dto)) {
            throw new IllegalArgumentException(nombreInteresado + " está incompleto. Debe tener nombre, DNI y rol.");
        }
    }

    /**
     * Verifica la integridad del conjunto de interesados antes de guardar un Expediente.
     * Regla estricta: Si se introducen dos interesados, no pueden tener el mismo DNI 
     * (un cliente no puede ser comprador y vendedor a la vez en el mismo trámite).
     */
    public void validarInteresados(InteresadoFormDto interesado1, InteresadoFormDto interesado2) {
        validarInteresado(interesado1, "Interesado 1");
        validarInteresado(interesado2, "Interesado 2");

        if (!interesadoVacio(interesado1) && !interesadoVacio(interesado2)) {
            if (interesado1.getDni().equalsIgnoreCase(interesado2.getDni())) {
                throw new IllegalArgumentException("Los dos interesados no pueden tener el mismo DNI.");
            }
        }
    }

    /**
     * Inicializa un Expediente validando y procesando a sus interesados.
     * Reglas clave:
     * - Verifica de forma estricta que los DNIs introducidos no sean idénticos.
     * - Si un interesado (por DNI) ya existe previamente en el sistema (ej. un cliente habitual),
     *   reutiliza su registro en base de datos. De lo contrario, lo crea desde cero antes de 
     *   asignarlo al expediente con su rol específico (Ej: VENDEDOR, COMPRADOR).
     */
    @Override
    @Transactional
    public Expediente crearExpedienteCompleto(Expediente expediente,
            Usuario usuarioLogueado,
            Long clienteId,
            Long tipoTramiteId,
            InteresadoFormDto interesado1,
            InteresadoFormDto interesado2) {

        validarInteresados(interesado1, interesado2);

        Cliente cliente = clienteService.buscarPorId(clienteId).orElseThrow();
        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId).orElseThrow();

        expediente.setCliente(cliente);
        expediente.setTipoTramite(tipoTramite);
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
        expediente.setCreadoPor(usuarioLogueado);

        Expediente expedienteGuardado = expedienteRepository.save(expediente);

        guardarInteresadoSiValido(expedienteGuardado, interesado1);
        guardarInteresadoSiValido(expedienteGuardado, interesado2);

        historialCambioService.registrarCambioExpediente(
                expedienteGuardado,
                usuarioLogueado,
                "CREACIÓN DIRECTA",
                "Expediente inicializado con el trámite " + tipoTramite.getNombre());

        return expedienteGuardado;
    }

    @Override
    @Transactional
    public Expediente actualizarExpediente(Long id, Expediente expedienteActualizado,
            Usuario usuarioLogueado,
            Long clienteId,
            Long tipoTramiteId,
            InteresadoFormDto interesado1,
            InteresadoFormDto interesado2) {

        Expediente expedienteBase = expedienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        if (!tienePermisoExpediente(expedienteBase, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }

        if (expedienteBase.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("No se puede editar un expediente finalizado");
        }

        validarInteresados(interesado1, interesado2);

        Cliente cliente = clienteService.buscarPorId(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de trámite no encontrado"));

        String matriculaAnterior = expedienteBase.getMatricula();
        String obsAnterior = expedienteBase.getObservaciones();
        Long tipoTramiteAnterior = expedienteBase.getTipoTramite() != null ? expedienteBase.getTipoTramite().getId()
                : null;

        expedienteBase.setCliente(cliente);
        expedienteBase.setTipoTramite(tipoTramite);
        expedienteBase.setMatricula(expedienteActualizado.getMatricula());
        expedienteBase.setObservaciones(expedienteActualizado.getObservaciones());

        // Limpiar asociaciones previas
        expedienteInteresadoRepository.deleteByExpedienteId(expedienteBase.getId());

        // Es necesario hacer el flush/clear o sencillamente como es LAZY en
        // expediente.getInteresados() no importa si no se lee en esta transaccion.
        // Pero para asegurar, simplemente lo guardamos despues.

        Expediente expedienteGuardado = expedienteRepository.save(expedienteBase);

        guardarInteresadoSiValido(expedienteGuardado, interesado1);
        guardarInteresadoSiValido(expedienteGuardado, interesado2);

        java.util.List<String> cambios = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(matriculaAnterior, expedienteActualizado.getMatricula())) {
            cambios.add("Matrícula");
        }
        if (!java.util.Objects.equals(obsAnterior, expedienteActualizado.getObservaciones())) {
            cambios.add("Observaciones");
        }
        if (!java.util.Objects.equals(tipoTramiteAnterior, tipoTramite.getId())) {
            cambios.add("Tipo de trámite (" + tipoTramite.getNombre() + ")");
        }

        historialCambioService.registrarCambioExpediente(
                expedienteGuardado,
                usuarioLogueado,
                "EDICIÓN",
                cambios.isEmpty() ? "Se editaron interesados u otros datos del expediente."
                        : "Se modificaron los campos: " + String.join(", ", cambios));

        return expedienteGuardado;
    }
}
