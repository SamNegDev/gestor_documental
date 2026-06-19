package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.dto.expediente.SolicitudInteresadoCoincidenciaResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HistorialCambioRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.MensajeRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.service.OperacionExpedienteService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.util.DireccionFormatter;
import com.example.gestor_documental.util.NombrePersonaNormalizer;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
    private final IncidenciaRepository incidenciaRepository;
    private final HistorialCambioRepository historialCambioRepository;
    private final MensajeRepository mensajeRepository;
    private final HistorialCambioService historialCambioService;
    private final InteresadoService interesadoService;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final OperacionExpedienteService operacionExpedienteService;
    private final ObjectProvider<RequisitoDocumentalExpedienteService> requisitoDocumentalExpedienteService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public List<Solicitud> listarTodas() {
        return solicitudRepository.findAllOrderByFechaReferenciaDesc();
    }

    @Override
    public Optional<Solicitud> buscarPorId(Long id) {
        return solicitudRepository.findById(id);
    }

    @Override
    public Solicitud guardar(Solicitud solicitud) {
        normalizarSolicitud(solicitud);
        return solicitudRepository.save(solicitud);
    }

    @Override
    public long contarTodos() {
        return solicitudRepository.count();
    }

    @Override
    public List<Solicitud> listarPorClienteId(Long clienteId) {
        return solicitudRepository.findByClienteIdOrderByFechaReferenciaDesc(clienteId);
    }

    @Override
    public Page<Solicitud> buscarListado(Long clienteId,
                                         EstadoSolicitud estado,
                                         String archivo,
                                         Long tipoTramiteId,
                                         String matricula,
                                         LocalDateTime desde,
                                         LocalDateTime hasta,
                                         Pageable pageable) {
        return solicitudRepository.buscarListado(clienteId, estado, normalizarArchivoListado(archivo), tipoTramiteId, matricula, desde, hasta, pageable);
    }

    private String normalizarArchivoListado(String archivo) {
        if (archivo == null || archivo.isBlank()) {
            return "ACTIVAS";
        }
        String normalizado = archivo.trim().toUpperCase();
        return switch (normalizado) {
            case "ACTIVAS", "ARCHIVADAS", "TODAS" -> normalizado;
            default -> "ACTIVAS";
        };
    }

    @Override
    public boolean tienePermisoSolicitud(Solicitud solicitud, Usuario usuario) {
        if (usuario == null) {
            return false;
        }
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
        return solicitudRepository.findTop5OrderByFechaReferenciaDesc();
    }

    @Override
    public List<Solicitud> listarUltimasPorCliente(Cliente cliente) {
        return solicitudRepository.findTop5ByClienteOrderByFechaReferenciaDesc(cliente);
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
        normalizarSolicitud(solicitud);

        Solicitud solicitudGuardada = solicitudRepository.save(solicitud);
        
        historialCambioService.registrarCambioSolicitud(
                solicitudGuardada, 
                usuarioLogueado, 
                "CREACIÓN DIRECTA", 
                "Solicitud inicializada con el trámite " + tipoTramite.getNombre()
        );

        return solicitudGuardada;
    }

    /**
     * Verifica la integridad de los interesados al crear o actualizar una Solicitud.
     * Regla estricta: Impide que los dos interesados (ej. titular y comprador) posean el mismo DNI.
     */
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

    /**
     * Valida que un bloque de interesado esté completamente vacío o completamente lleno.
     * No se permite dejar huérfanos datos parciales de un interesado (ej. solo el nombre pero sin DNI ni rol).
     */
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

    /**
     * Transforma una Solicitud validada en un Expediente formal.
     * Reglas clave:
     * - Bloquea la conversión si la solicitud está RECHAZADA, ya CONVERTIDA o ya posee un expediente.
     * - Mapea los interesados planos de la solicitud al formato DTO para aplicar la validación 
     *   estricta de duplicidad de DNIs propia del núcleo de Expedientes.
     * - Traspasa la propiedad de los Documentos asociados de la Solicitud al nuevo Expediente sin duplicar archivos.
     */
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
        expediente.setMatricula(TextNormalizer.upperOrNull(solicitud.getMatricula()));
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);
        expediente.setObservaciones(TextNormalizer.upperOrNull(solicitud.getObservaciones()));
        expediente.setCreadoPor(admin);

        Expediente expedienteGuardado = expedienteRepository.save(expediente);
        historialCambioService.registrarCambioExpediente(
                expedienteGuardado,
                admin,
                "CREACIÓN DESDE SOLICITUD",
                "Expediente creado desde solicitud ID" + solicitud.getId());


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
         interesado1dto.setTipoVia(solicitud.getInteresado1TipoVia());
         interesado1dto.setNombreVia(solicitud.getInteresado1NombreVia());
         interesado1dto.setCodigoPostal(solicitud.getInteresado1CodigoPostal());
         interesado1dto.setMunicipio(solicitud.getInteresado1Municipio());
         interesado1dto.setProvincia(solicitud.getInteresado1Provincia());

         InteresadoFormDto interesado2dto = new InteresadoFormDto();
         interesado2dto.setDni(solicitud.getInteresado2Dni());
         interesado2dto.setNombre(solicitud.getInteresado2Nombre());
         interesado2dto.setRol(solicitud.getInteresado2Rol());
         interesado2dto.setDireccion(solicitud.getInteresado2Direccion());
         interesado2dto.setTelefono(solicitud.getInteresado2Telefono());
         interesado2dto.setTipoVia(solicitud.getInteresado2TipoVia());
         interesado2dto.setNombreVia(solicitud.getInteresado2NombreVia());
         interesado2dto.setCodigoPostal(solicitud.getInteresado2CodigoPostal());
         interesado2dto.setMunicipio(solicitud.getInteresado2Municipio());
         interesado2dto.setProvincia(solicitud.getInteresado2Provincia());

        expedienteService.validarInteresados(interesado1dto, interesado2dto);

        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado1dto);
        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado2dto);
        sincronizarDocumentacionExpedienteConvertido(expedienteGuardado, admin);
        
        historialCambioService.registrarCambioSolicitud(
                solicitud, 
                admin, 
                "CONVERSIÓN", 
                "Se ha convertido la solicitud a Expediente #" + expedienteGuardado.getId()
        );

        return expedienteGuardado;
    }

    private void sincronizarDocumentacionExpedienteConvertido(Expediente expediente, Usuario admin) {
        operacionExpedienteService.sincronizarYListar(expediente);
        requisitoDocumentalExpedienteService.getObject().sincronizarYListar(
                expediente,
                expedienteInteresadoRepository.findByExpedienteId(expediente.getId()),
                documentoRepository.findByExpedienteId(expediente.getId()),
                admin
        );
    }

    @Override
    public List<SolicitudInteresadoCoincidenciaResponse> buscarCoincidenciasInteresadosConDiferencias(Long solicitudId, Usuario admin) {
        if (admin == null || admin.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede revisar coincidencias de interesados");
        }
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (!tienePermisoSolicitud(solicitud, admin)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a esta solicitud");
        }

        return List.of(
                        construirCoincidenciaSiHayDiferencias(
                                solicitud.getInteresado1Rol(),
                                solicitud.getInteresado1Dni(),
                                solicitud.getInteresado1Nombre(),
                                solicitud.getInteresado1Telefono(),
                                direccionSolicitud(
                                        solicitud.getInteresado1Direccion(),
                                        solicitud.getInteresado1TipoVia(),
                                        solicitud.getInteresado1NombreVia(),
                                        solicitud.getInteresado1CodigoPostal(),
                                        solicitud.getInteresado1Municipio(),
                                        solicitud.getInteresado1Provincia())
                        ),
                        construirCoincidenciaSiHayDiferencias(
                                solicitud.getInteresado2Rol(),
                                solicitud.getInteresado2Dni(),
                                solicitud.getInteresado2Nombre(),
                                solicitud.getInteresado2Telefono(),
                                direccionSolicitud(
                                        solicitud.getInteresado2Direccion(),
                                        solicitud.getInteresado2TipoVia(),
                                        solicitud.getInteresado2NombreVia(),
                                        solicitud.getInteresado2CodigoPostal(),
                                        solicitud.getInteresado2Municipio(),
                                        solicitud.getInteresado2Provincia())
                        )
                ).stream()
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SolicitudInteresadoCoincidenciaResponse> construirCoincidenciaSiHayDiferencias(
            RolInteresado rol,
            String dni,
            String nombreDeclarado,
            String telefonoDeclarado,
            String direccionDeclarada
    ) {
        if (dni == null || dni.isBlank()) {
            return Optional.empty();
        }
        return interesadoService.buscarInteresadoPorDNI(dni)
                .flatMap(interesado -> {
                    List<String> diferencias = new java.util.ArrayList<>();
                    if (valorNombreAportadoDiferente(nombreDeclarado, interesado.getNombre())) {
                        diferencias.add("Nombre completo/Razon social");
                    }
                    if (valorAportadoDiferente(telefonoDeclarado, interesado.getTelefono())) {
                        diferencias.add("Telefono");
                    }
                    if (valorAportadoDiferente(direccionDeclarada, interesado.getDireccion())) {
                        diferencias.add("Direccion");
                    }
                    if (diferencias.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(SolicitudInteresadoCoincidenciaResponse.builder()
                            .rol(rol != null ? rol.name() : null)
                            .dni(interesado.getDni())
                            .nombreRegistrado(interesado.getNombre())
                            .nombreDeclarado(TextNormalizer.upperOrNull(nombreDeclarado))
                            .telefonoRegistrado(interesado.getTelefono())
                            .telefonoDeclarado(TextNormalizer.upperOrNull(telefonoDeclarado))
                            .direccionRegistrada(interesado.getDireccion())
                            .direccionDeclarada(TextNormalizer.upperOrNull(direccionDeclarada))
                            .camposDiferentes(diferencias)
                            .build());
                });
    }

    private boolean valorAportadoDiferente(String declarado, String registrado) {
        String declaradoNormalizado = TextNormalizer.upperOrNull(declarado);
        if (declaradoNormalizado == null || declaradoNormalizado.isBlank()) {
            return false;
        }
        String registradoNormalizado = TextNormalizer.upperOrNull(registrado);
        return !declaradoNormalizado.equals(registradoNormalizado);
    }

    private boolean valorNombreAportadoDiferente(String declarado, String registrado) {
        String declaradoNormalizado = NombrePersonaNormalizer.normalizar(declarado);
        if (declaradoNormalizado == null || declaradoNormalizado.isBlank()) {
            return false;
        }
        String registradoNormalizado = NombrePersonaNormalizer.normalizar(registrado);
        return !declaradoNormalizado.equals(registradoNormalizado);
    }

    /**
     * Modifica el estado de una Solicitud aplicando bloqueos de seguridad y negocio.
     * Reglas clave:
     * - Impide alteraciones manuales si la solicitud ya finalizó su ciclo (CONVERTIDA o RECHAZADA).
     * - Si se intenta retomar su tramitación (volver a PENDIENTE_REVISION), el sistema aborta 
     *   la acción si aún existen incidencias en rojo sin subsanar.
     */
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

        if (nuevoEstado == EstadoSolicitud.PENDIENTE_REVISION) {
            long incidenciasActivas = incidenciaRepository.findBySolicitudIdAndResueltaFalse(solicitud.getId()).size();
            if (incidenciasActivas > 0) {
                throw new OperacionInvalidaException("No se puede volver a revisión una solicitud con incidencias activas sin resolver.");
            }
        }
        
        EstadoSolicitud estadoAnterior = solicitud.getEstadoSolicitud();
        
        if (estadoAnterior == nuevoEstado) {
            return;
        }

        solicitud.setEstadoSolicitud(nuevoEstado);
        solicitudRepository.save(solicitud);
        
        historialCambioService.registrarCambioSolicitud(
                solicitud, 
                usuarioLogueado, 
                "CAMBIO ESTADO", 
                "El estado cambió de '" + estadoAnterior.name() + "' a '" + nuevoEstado.name() + "'"
        );
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

        String matriculaAnterior = solicitudBase.getMatricula();
        Long tipoTramiteAnterior = solicitudBase.getTipoTramite() != null ? solicitudBase.getTipoTramite().getId() : null;

        solicitudBase.setTipoTramite(tipoTramite);
        solicitudBase.setMatricula(TextNormalizer.upperOrNull(solicitudActualizada.getMatricula()));

        solicitudBase.setInteresado1Rol(solicitudActualizada.getInteresado1Rol());
        solicitudBase.setInteresado1Nombre(NombrePersonaNormalizer.normalizar(solicitudActualizada.getInteresado1Nombre()));
        solicitudBase.setInteresado1Dni(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1Dni()));
        solicitudBase.setInteresado1Telefono(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1Telefono()));
        solicitudBase.setInteresado1TipoVia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1TipoVia()));
        solicitudBase.setInteresado1NombreVia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1NombreVia()));
        solicitudBase.setInteresado1CodigoPostal(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1CodigoPostal()));
        solicitudBase.setInteresado1Municipio(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1Municipio()));
        solicitudBase.setInteresado1Provincia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado1Provincia()));
        solicitudBase.setInteresado1Direccion(direccionSolicitud(
                solicitudActualizada.getInteresado1Direccion(),
                solicitudActualizada.getInteresado1TipoVia(),
                solicitudActualizada.getInteresado1NombreVia(),
                solicitudActualizada.getInteresado1CodigoPostal(),
                solicitudActualizada.getInteresado1Municipio(),
                solicitudActualizada.getInteresado1Provincia()));

        solicitudBase.setInteresado2Rol(solicitudActualizada.getInteresado2Rol());
        solicitudBase.setInteresado2Nombre(NombrePersonaNormalizer.normalizar(solicitudActualizada.getInteresado2Nombre()));
        solicitudBase.setInteresado2Dni(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2Dni()));
        solicitudBase.setInteresado2Telefono(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2Telefono()));
        solicitudBase.setInteresado2TipoVia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2TipoVia()));
        solicitudBase.setInteresado2NombreVia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2NombreVia()));
        solicitudBase.setInteresado2CodigoPostal(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2CodigoPostal()));
        solicitudBase.setInteresado2Municipio(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2Municipio()));
        solicitudBase.setInteresado2Provincia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado2Provincia()));
        solicitudBase.setInteresado2Direccion(direccionSolicitud(
                solicitudActualizada.getInteresado2Direccion(),
                solicitudActualizada.getInteresado2TipoVia(),
                solicitudActualizada.getInteresado2NombreVia(),
                solicitudActualizada.getInteresado2CodigoPostal(),
                solicitudActualizada.getInteresado2Municipio(),
                solicitudActualizada.getInteresado2Provincia()));

        solicitudBase.setObservaciones(TextNormalizer.upperOrNull(solicitudActualizada.getObservaciones()));

        java.util.List<String> cambios = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(matriculaAnterior, solicitudActualizada.getMatricula())) {
            cambios.add("Matrícula");
        }
        if (!java.util.Objects.equals(tipoTramiteAnterior, tipoTramite.getId())) {
            cambios.add("Tipo de trámite (" + tipoTramite.getNombre() + ")");
        }
        
        Solicitud solicitudGuardada = solicitudRepository.save(solicitudBase);
        
        historialCambioService.registrarCambioSolicitud(
                solicitudGuardada, 
                usuarioLogueado, 
                "EDICIÓN", 
                cambios.isEmpty() ? "Se editaron interesados u otros datos." : "Se modificaron los campos: " + String.join(", ", cambios)
        );

        return solicitudGuardada;
    }

    @Override
    @Transactional
    public void eliminarSolicitudErronea(Long id, Usuario admin) {
        if (admin == null || admin.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede eliminar solicitudes");
        }

        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA || solicitud.getExpediente() != null) {
            throw new OperacionInvalidaException("No se puede eliminar una solicitud convertida o con expediente asociado");
        }

        List<Long> documentoIds = new java.util.ArrayList<>();
        documentoIds.addAll(documentoRepository.findBySolicitudId(id).stream().map(Documento::getId).toList());
        for (Incidencia incidencia : incidenciaRepository.findBySolicitudId(id)) {
            documentoIds.addAll(documentoRepository.findByIncidenciaId(incidencia.getId()).stream().map(Documento::getId).toList());
        }
        for (Long documentoId : documentoIds.stream().distinct().toList()) {
            eliminarDocumentoSolicitud(documentoId);
        }

        incidenciaRepository.deleteAll(incidenciaRepository.findBySolicitudId(id));
        mensajeRepository.deleteBySolicitudId(id);
        historialCambioRepository.deleteBySolicitudId(id);
        solicitudRepository.delete(solicitud);
    }

    private void eliminarDocumentoSolicitud(Long documentoId) {
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado"));
        Path carpetaUploads = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path rutaArchivo = carpetaUploads.resolve(documento.getNombreArchivo()).normalize();
        if (!rutaArchivo.startsWith(carpetaUploads)) {
            throw new OperacionInvalidaException("Ruta de archivo no permitida");
        }
        try {
            if (Files.exists(rutaArchivo)) {
                Files.delete(rutaArchivo);
            }
        } catch (IOException exception) {
            throw new RuntimeException("Error al borrar el archivo fisico", exception);
        }
        documentoRepository.delete(documento);
    }

    private void normalizarSolicitud(Solicitud solicitud) {
        solicitud.setMatricula(TextNormalizer.upperOrNull(solicitud.getMatricula()));
        solicitud.setObservaciones(TextNormalizer.upperOrNull(solicitud.getObservaciones()));
        solicitud.setInteresado1Nombre(NombrePersonaNormalizer.normalizar(solicitud.getInteresado1Nombre()));
        solicitud.setInteresado1Dni(TextNormalizer.upperOrNull(solicitud.getInteresado1Dni()));
        solicitud.setInteresado1Telefono(TextNormalizer.upperOrNull(solicitud.getInteresado1Telefono()));
        solicitud.setInteresado1TipoVia(TextNormalizer.upperOrNull(solicitud.getInteresado1TipoVia()));
        solicitud.setInteresado1NombreVia(TextNormalizer.upperOrNull(solicitud.getInteresado1NombreVia()));
        solicitud.setInteresado1CodigoPostal(TextNormalizer.upperOrNull(solicitud.getInteresado1CodigoPostal()));
        solicitud.setInteresado1Municipio(TextNormalizer.upperOrNull(solicitud.getInteresado1Municipio()));
        solicitud.setInteresado1Provincia(TextNormalizer.upperOrNull(solicitud.getInteresado1Provincia()));
        solicitud.setInteresado1Direccion(direccionSolicitud(
                solicitud.getInteresado1Direccion(),
                solicitud.getInteresado1TipoVia(),
                solicitud.getInteresado1NombreVia(),
                solicitud.getInteresado1CodigoPostal(),
                solicitud.getInteresado1Municipio(),
                solicitud.getInteresado1Provincia()));
        solicitud.setInteresado2Nombre(NombrePersonaNormalizer.normalizar(solicitud.getInteresado2Nombre()));
        solicitud.setInteresado2Dni(TextNormalizer.upperOrNull(solicitud.getInteresado2Dni()));
        solicitud.setInteresado2Telefono(TextNormalizer.upperOrNull(solicitud.getInteresado2Telefono()));
        solicitud.setInteresado2TipoVia(TextNormalizer.upperOrNull(solicitud.getInteresado2TipoVia()));
        solicitud.setInteresado2NombreVia(TextNormalizer.upperOrNull(solicitud.getInteresado2NombreVia()));
        solicitud.setInteresado2CodigoPostal(TextNormalizer.upperOrNull(solicitud.getInteresado2CodigoPostal()));
        solicitud.setInteresado2Municipio(TextNormalizer.upperOrNull(solicitud.getInteresado2Municipio()));
        solicitud.setInteresado2Provincia(TextNormalizer.upperOrNull(solicitud.getInteresado2Provincia()));
        solicitud.setInteresado2Direccion(direccionSolicitud(
                solicitud.getInteresado2Direccion(),
                solicitud.getInteresado2TipoVia(),
                solicitud.getInteresado2NombreVia(),
                solicitud.getInteresado2CodigoPostal(),
                solicitud.getInteresado2Municipio(),
                solicitud.getInteresado2Provincia()));
    }

    private String direccionSolicitud(String direccion, String tipoVia, String nombreVia, String codigoPostal, String municipio, String provincia) {
        String direccionNormalizada = TextNormalizer.upperOrNull(direccion);
        if (direccionNormalizada != null) {
            return direccionNormalizada;
        }
        return DireccionFormatter.componer(tipoVia, nombreVia, codigoPostal, municipio, provincia);
    }
}

