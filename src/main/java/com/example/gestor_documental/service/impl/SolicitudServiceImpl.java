package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.dto.expediente.SolicitudIdentidadDetectadaRequest;
import com.example.gestor_documental.dto.expediente.SolicitudInteresadoHabitualRequest;
import com.example.gestor_documental.dto.expediente.SolicitudInteresadoCoincidenciaResponse;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
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
import com.example.gestor_documental.service.VehiculoService;
import com.example.gestor_documental.util.DireccionFormatter;
import com.example.gestor_documental.util.DireccionNormalizer;
import com.example.gestor_documental.util.NombrePersonaNormalizer;
import com.example.gestor_documental.util.TextNormalizer;
import com.example.gestor_documental.validation.DniNieValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static final double CONFIANZA_IDENTIDAD_VALIDADA_MANUALMENTE = 1.0;

    private final SolicitudRepository solicitudRepository;
    private final TipoTramiteService tipoTramiteService;
    private final ExpedienteRepository expedienteRepository;
    private final ExpedienteService expedienteService;
    private final DocumentoRepository documentoRepository;
    private final DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final HistorialCambioRepository historialCambioRepository;
    private final MensajeRepository mensajeRepository;
    private final HistorialCambioService historialCambioService;
    private final InteresadoService interesadoService;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final OperacionExpedienteService operacionExpedienteService;
    private final VehiculoService vehiculoService;
    private final ObjectProvider<RequisitoDocumentalExpedienteService> requisitoDocumentalExpedienteService;
    private final DniNieValidator dniNieValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        validarInteresadoSolicitud(
                solicitud.getInteresado3Nombre(),
                solicitud.getInteresado3Dni(),
                solicitud.getInteresado3Rol(),
                "Interesado 3"
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

        boolean interesado3Informado = !interesadoSolicitudVacio(
                solicitud.getInteresado3Nombre(),
                solicitud.getInteresado3Dni(),
                solicitud.getInteresado3Rol()
        );

        java.util.List<String> dnisInformados = new java.util.ArrayList<>();
        if (interesado1Informado) {
            dnisInformados.add(TextNormalizer.upperOrNull(solicitud.getInteresado1Dni()));
        }
        if (interesado2Informado) {
            dnisInformados.add(TextNormalizer.upperOrNull(solicitud.getInteresado2Dni()));
        }
        if (interesado3Informado) {
            dnisInformados.add(TextNormalizer.upperOrNull(solicitud.getInteresado3Dni()));
        }
        for (int i = 0; i < dnisInformados.size(); i++) {
            for (int j = i + 1; j < dnisInformados.size(); j++) {
                if (dnisInformados.get(i) != null && dnisInformados.get(i).equalsIgnoreCase(dnisInformados.get(j))) {
                    throw new IllegalArgumentException("Los interesados no pueden tener el mismo DNI/CIF.");
                }
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
        Vehiculo vehiculo = vehiculoService.obtenerOCrearPorMatricula(expediente.getMatricula());
        completarVehiculoDesdeSolicitud(vehiculo, solicitud);
        expediente.setVehiculo(vehiculo);

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

         InteresadoFormDto interesado3dto = new InteresadoFormDto();
         interesado3dto.setDni(solicitud.getInteresado3Dni());
         interesado3dto.setNombre(solicitud.getInteresado3Nombre());
         interesado3dto.setRol(solicitud.getInteresado3Rol());
         interesado3dto.setDireccion(solicitud.getInteresado3Direccion());
         interesado3dto.setTelefono(solicitud.getInteresado3Telefono());
         interesado3dto.setTipoVia(solicitud.getInteresado3TipoVia());
         interesado3dto.setNombreVia(solicitud.getInteresado3NombreVia());
         interesado3dto.setCodigoPostal(solicitud.getInteresado3CodigoPostal());
         interesado3dto.setMunicipio(solicitud.getInteresado3Municipio());
         interesado3dto.setProvincia(solicitud.getInteresado3Provincia());

        expedienteService.validarInteresados(List.of(interesado1dto, interesado2dto, interesado3dto));

        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado1dto);
        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado2dto);
        expedienteService.guardarInteresadoSiValido(expedienteGuardado, interesado3dto);
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
                        ),
                        construirCoincidenciaSiHayDiferencias(
                                solicitud.getInteresado3Rol(),
                                solicitud.getInteresado3Dni(),
                                solicitud.getInteresado3Nombre(),
                                solicitud.getInteresado3Telefono(),
                                direccionSolicitud(
                                        solicitud.getInteresado3Direccion(),
                                        solicitud.getInteresado3TipoVia(),
                                        solicitud.getInteresado3NombreVia(),
                                        solicitud.getInteresado3CodigoPostal(),
                                        solicitud.getInteresado3Municipio(),
                                        solicitud.getInteresado3Provincia())
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
                    if (direccionAportadaDiferente(direccionDeclarada, interesado.getDireccion())) {
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

    private boolean direccionAportadaDiferente(String declarada, String registrada) {
        String declaradaNormalizada = DireccionNormalizer.normalizar(declarada);
        if (declaradaNormalizada == null || declaradaNormalizada.isBlank()) {
            return false;
        }
        String registradaNormalizada = DireccionNormalizer.normalizar(registrada);
        return !declaradaNormalizada.equals(registradaNormalizada);
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
        String vehiculoMarcaAnterior = solicitudBase.getVehiculoMarca();
        String vehiculoModeloAnterior = solicitudBase.getVehiculoModelo();
        String vehiculoBastidorAnterior = solicitudBase.getVehiculoBastidor();
        String operacionPrecioVentaAnterior = solicitudBase.getOperacionPrecioVenta();
        Long tipoTramiteAnterior = solicitudBase.getTipoTramite() != null ? solicitudBase.getTipoTramite().getId() : null;

        solicitudBase.setTipoTramite(tipoTramite);
        solicitudBase.setMatricula(TextNormalizer.upperOrNull(solicitudActualizada.getMatricula()));
        solicitudBase.setVehiculoMarca(TextNormalizer.upperOrNull(solicitudActualizada.getVehiculoMarca()));
        solicitudBase.setVehiculoModelo(TextNormalizer.upperOrNull(solicitudActualizada.getVehiculoModelo()));
        solicitudBase.setVehiculoBastidor(normalizarCodigoVehiculo(solicitudActualizada.getVehiculoBastidor()));
        solicitudBase.setOperacionPrecioVenta(TextNormalizer.upperOrNull(solicitudActualizada.getOperacionPrecioVenta()));

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

        solicitudBase.setInteresado3Rol(solicitudActualizada.getInteresado3Rol());
        solicitudBase.setInteresado3Nombre(NombrePersonaNormalizer.normalizar(solicitudActualizada.getInteresado3Nombre()));
        solicitudBase.setInteresado3Dni(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3Dni()));
        solicitudBase.setInteresado3Telefono(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3Telefono()));
        solicitudBase.setInteresado3TipoVia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3TipoVia()));
        solicitudBase.setInteresado3NombreVia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3NombreVia()));
        solicitudBase.setInteresado3CodigoPostal(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3CodigoPostal()));
        solicitudBase.setInteresado3Municipio(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3Municipio()));
        solicitudBase.setInteresado3Provincia(TextNormalizer.upperOrNull(solicitudActualizada.getInteresado3Provincia()));
        solicitudBase.setInteresado3Direccion(direccionSolicitud(
                solicitudActualizada.getInteresado3Direccion(),
                solicitudActualizada.getInteresado3TipoVia(),
                solicitudActualizada.getInteresado3NombreVia(),
                solicitudActualizada.getInteresado3CodigoPostal(),
                solicitudActualizada.getInteresado3Municipio(),
                solicitudActualizada.getInteresado3Provincia()));

        solicitudBase.setObservaciones(TextNormalizer.upperOrNull(solicitudActualizada.getObservaciones()));

        java.util.List<String> cambios = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(matriculaAnterior, solicitudActualizada.getMatricula())) {
            cambios.add("Matrícula");
        }
        if (!java.util.Objects.equals(tipoTramiteAnterior, tipoTramite.getId())) {
            cambios.add("Tipo de trámite (" + tipoTramite.getNombre() + ")");
        }
        
        if (!java.util.Objects.equals(vehiculoMarcaAnterior, solicitudBase.getVehiculoMarca())
                || !java.util.Objects.equals(vehiculoModeloAnterior, solicitudBase.getVehiculoModelo())
                || !java.util.Objects.equals(vehiculoBastidorAnterior, solicitudBase.getVehiculoBastidor())) {
            cambios.add("Vehiculo");
        }
        if (!java.util.Objects.equals(operacionPrecioVentaAnterior, solicitudBase.getOperacionPrecioVenta())) {
            cambios.add("Precio de venta");
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
    public Solicitud resetDatosLecturaIa(Long id, Usuario admin) {
        if (admin == null || admin.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede resetear datos de lectura IA");
        }
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA ||
                solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO ||
                solicitud.getExpediente() != null) {
            throw new OperacionInvalidaException("No se pueden resetear datos de una solicitud cerrada");
        }

        limpiarInteresado1(solicitud);
        limpiarInteresado2(solicitud);
        limpiarInteresado3(solicitud);
        solicitud.setVehiculoMarca(null);
        solicitud.setVehiculoModelo(null);
        solicitud.setVehiculoBastidor(null);
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(admin);

        Solicitud guardada = solicitudRepository.save(solicitud);
        historialCambioService.registrarCambioSolicitud(
                guardada,
                admin,
                "RESET IA DOCUMENTACION",
                "Se vaciaron interesados y datos de vehiculo para repetir la lectura IA.");
        return guardada;
    }

    @Override
    @Transactional
    public Solicitud anadirInteresadoDetectado(Long id, SolicitudIdentidadDetectadaRequest request, Usuario usuarioLogueado) {
        if (request == null || request.getRol() == null) {
            throw new OperacionInvalidaException("Selecciona el rol del interesado");
        }

        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (!tienePermisoSolicitud(solicitud, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para modificar esta solicitud");
        }
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA ||
                solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede editar una solicitud convertida o rechazada");
        }

        String identificador = TextNormalizer.upperOrNull(request.getIdentificador());
        if (identificador != null) {
            identificador = identificador.replaceAll("[^A-Z0-9]", "");
        }
        if (identificador == null || identificador.isBlank()) {
            throw new OperacionInvalidaException("La identidad detectada no tiene DNI/CIF legible");
        }
        if (!identificadorDocumentoValido(identificador)) {
            throw new OperacionInvalidaException("El DNI/NIE/CIF indicado no es valido. Corrigelo antes de anadirlo.");
        }
        if (dniYaPresente(solicitud, identificador)) {
            throw new OperacionInvalidaException("Ese DNI/CIF ya esta incluido en la solicitud");
        }

        String nombre = nombreInteresadoDetectado(request, identificador);
        int slot = primerHuecoInteresado(solicitud);
        if (slot == 0) {
            throw new OperacionInvalidaException("La solicitud ya tiene los tres interesados ocupados");
        }

        validarLecturaIdentidadManual(solicitud, request, identificador);
        aplicarInteresadoDetectado(solicitud, slot, request.getRol(), nombre, identificador, request.getDireccionTexto());
        validarInteresadosSolicitud(solicitud);
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(usuarioLogueado);
        Solicitud guardada = solicitudRepository.save(solicitud);
        historialCambioService.registrarCambioSolicitud(
                guardada,
                usuarioLogueado,
                "IDENTIDAD DETECTADA",
                "Se incorporo " + request.getRol().name() + " " + nombre + " (" + identificador + ") desde lectura de documento validada."
        );
        return guardada;
    }

    @Override
    @Transactional
    public Solicitud asignarInteresadoHabitual(Long id, SolicitudInteresadoHabitualRequest request, Usuario usuarioLogueado) {
        if (request == null || request.getInteresadoId() == null || request.getRol() == null) {
            throw new OperacionInvalidaException("Selecciona un interesado habitual y el rol que ocupa");
        }

        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (!tienePermisoSolicitud(solicitud, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para modificar esta solicitud");
        }
        if (solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA ||
                solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO) {
            throw new OperacionInvalidaException("No se puede editar una solicitud convertida o rechazada");
        }
        if (solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
            throw new OperacionInvalidaException("La solicitud no tiene cliente asociado");
        }

        ClienteInteresado relacion = clienteInteresadoRepository
                .findByClienteIdAndInteresadoIdAndHabitualTrue(solicitud.getCliente().getId(), request.getInteresadoId())
                .orElseThrow(() -> new AccesoDenegadoException("El interesado no pertenece a la cartera habitual del cliente"));
        Interesado interesado = relacion.getInteresado();
        if (interesado == null) {
            throw new RecursoNoEncontradoException("Interesado habitual no encontrado");
        }
        String identificador = TextNormalizer.upperOrNull(interesado.getDni());
        if (identificador != null) {
            identificador = identificador.replaceAll("[^A-Z0-9]", "");
        }
        String nombre = NombrePersonaNormalizer.normalizar(interesado.getNombre());
        if (identificador == null || nombre == null) {
            throw new OperacionInvalidaException("El interesado habitual necesita DNI/CIF y nombre para asignarse");
        }

        int slot = slotParaInteresadoHabitual(solicitud, request.getRol(), interesado, identificador);
        if (slot == 0) {
            throw new OperacionInvalidaException("La solicitud ya tiene los tres interesados ocupados");
        }

        aplicarInteresadoHabitual(solicitud, slot, request.getRol(), interesado, identificador, nombre);
        validarInteresadosSolicitud(solicitud);
        normalizarSolicitud(solicitud);
        solicitud.setFechaUltimaModificacion(LocalDateTime.now());
        solicitud.setModificadoPor(usuarioLogueado);
        Solicitud guardada = solicitudRepository.save(solicitud);
        historialCambioService.registrarCambioSolicitud(
                guardada,
                usuarioLogueado,
                "INTERESADO HABITUAL",
                "Se asigno " + request.getRol().name() + " " + nombre + " (" + identificador + ") desde la cartera habitual del cliente."
        );
        return guardada;
    }

    private void validarLecturaIdentidadManual(Solicitud solicitud, SolicitudIdentidadDetectadaRequest request, String identificador) {
        if (request.getDocumentoId() == null) {
            return;
        }
        Documento documento = documentoRepository.findByIdConRelaciones(request.getDocumentoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento de identidad no encontrado"));
        if (documento.getSolicitud() == null || solicitud.getId() == null
                || !solicitud.getId().equals(documento.getSolicitud().getId())) {
            throw new AccesoDenegadoException("El documento no pertenece a esta solicitud");
        }
        if (documento.getTipoDocumento() != TipoDocumento.DNI && documento.getTipoDocumento() != TipoDocumento.CIF) {
            throw new OperacionInvalidaException("Solo se puede validar identidad sobre documentos DNI o CIF.");
        }
        TipoDocumento tipoDetectado = tipoIdentidadDetectado(request.getTipoDocumentoDetectado(), documento.getTipoDocumento(), identificador);
        DocumentoIdentidadLectura lectura = documentoIdentidadLecturaRepository.findByDocumentoId(documento.getId())
                .orElseGet(DocumentoIdentidadLectura::new);
        lectura.setDocumento(documento);
        lectura.setTipoDocumentoDetectado(tipoDetectado);
        lectura.setIdentificador(identificador);
        lectura.setNombre(limitarTexto(request.getNombre(), 160));
        lectura.setApellido1(limitarTexto(request.getApellido1(), 160));
        lectura.setApellido2(limitarTexto(request.getApellido2(), 160));
        lectura.setRazonSocial(limitarTexto(request.getRazonSocial(), 220));
        lectura.setFechaNacimiento(limitarTexto(request.getFechaNacimiento(), 20));
        lectura.setFechaCaducidad(limitarTexto(request.getFechaCaducidad(), 20));
        lectura.setDireccionTexto(limitarTexto(request.getDireccionTexto(), 500));
        lectura.setConfianzaGlobal(CONFIANZA_IDENTIDAD_VALIDADA_MANUALMENTE);
        lectura.setRequiereRevision(false);
        lectura.setVinculadoAutomaticamente(false);
        lectura.setInteresadoVinculado(null);
        lectura.setFechaLectura(LocalDateTime.now());
        lectura.setMensaje("Identidad corregida y validada manualmente.");
        lectura.setResultadoJson(resultadoIdentidadManual(request, identificador, tipoDetectado));
        documentoIdentidadLecturaRepository.save(lectura);
        if (documento.getTipoDocumento() != tipoDetectado) {
            documento.setTipoDocumento(tipoDetectado);
            documentoRepository.save(documento);
        }
    }

    private TipoDocumento tipoIdentidadDetectado(String tipoSolicitado, TipoDocumento fallback, String identificador) {
        String normalizado = TextNormalizer.upperOrNull(tipoSolicitado);
        if (normalizado != null && normalizado.contains("CIF")) {
            return TipoDocumento.CIF;
        }
        if (normalizado != null && (normalizado.contains("DNI") || normalizado.contains("NIE"))) {
            return TipoDocumento.DNI;
        }
        return esCif(identificador) ? TipoDocumento.CIF : fallback != null ? fallback : TipoDocumento.DNI;
    }

    private String resultadoIdentidadManual(SolicitudIdentidadDetectadaRequest request, String identificador, TipoDocumento tipoDetectado) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode identidades = objectMapper.createArrayNode();
        ObjectNode identidad = objectMapper.createObjectNode();
        identidad.put("tipoDocumento", tipoDetectado == TipoDocumento.CIF ? "CIF" : tipoNieDni(identificador));
        identidad.put("identificador", identificador);
        putNullable(identidad, "nombre", request.getNombre());
        putNullable(identidad, "apellido1", request.getApellido1());
        putNullable(identidad, "apellido2", request.getApellido2());
        putNullable(identidad, "razonSocial", request.getRazonSocial());
        putNullable(identidad, "fechaNacimiento", request.getFechaNacimiento());
        putNullable(identidad, "fechaCaducidad", request.getFechaCaducidad());
        putNullable(identidad, "direccionTexto", request.getDireccionTexto());
        identidad.put("confianzaGlobal", CONFIANZA_IDENTIDAD_VALIDADA_MANUALMENTE);
        identidad.put("requiereRevision", false);
        identidad.put("observaciones", "Identidad corregida y validada manualmente.");
        identidades.add(identidad);
        root.set("identidades", identidades);
        root.put("observaciones", "Lectura corregida manualmente desde revision de solicitud.");
        return root.toString();
    }

    private JsonNode nullNode() {
        return objectMapper.nullNode();
    }

    private void putNullable(ObjectNode node, String field, String value) {
        String normalizado = value != null ? value.trim() : null;
        if (normalizado == null || normalizado.isBlank()) {
            node.set(field, nullNode());
            return;
        }
        node.put(field, normalizado);
    }

    private String tipoNieDni(String identificador) {
        return identificador != null && identificador.matches("[XYZ][0-9]{7}[A-Z]") ? "NIE" : "DNI";
    }

    private boolean identificadorDocumentoValido(String value) {
        String identificador = normalizarIdentificadorDocumento(value);
        if (identificador == null) {
            return false;
        }
        if (identificador.matches("[0-9]{8}[A-Z]") || identificador.matches("[XYZ][0-9]{7}[A-Z]")) {
            return dniNieValidator.esValido(identificador);
        }
        return esCif(identificador);
    }

    private boolean esCif(String identificador) {
        return identificador != null && identificador.matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private String normalizarIdentificadorDocumento(String value) {
        if (value == null) {
            return null;
        }
        String normalizado = value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }

    private String limitarTexto(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private int slotParaInteresadoHabitual(Solicitud solicitud, RolInteresado rol, Interesado interesado, String identificador) {
        int slotMismoDni = slotPorDni(solicitud, identificador);
        if (slotMismoDni != 0) {
            RolInteresado rolActual = rolSlot(solicitud, slotMismoDni);
            if (rolActual != null && rolActual != rol) {
                throw new OperacionInvalidaException("Ese DNI/CIF ya esta incluido con otro rol en la solicitud");
            }
            return slotMismoDni;
        }

        int slotRol = slotPorRol(solicitud, rol);
        if (slotRol != 0) {
            String dniActual = dniSlot(solicitud, slotRol);
            if (TextNormalizer.upperOrNull(dniActual) != null && !identificador.equals(TextNormalizer.upperOrNull(dniActual))) {
                throw new OperacionInvalidaException("Ya existe otro interesado como " + rol.name() + " en la solicitud");
            }
            String nombreActual = nombreSlot(solicitud, slotRol);
            if (TextNormalizer.upperOrNull(nombreActual) != null
                    && !NombrePersonaNormalizer.equivalentes(nombreActual, interesado.getNombre())) {
                throw new OperacionInvalidaException("El rol " + rol.name() + " ya tiene otro nombre informado");
            }
            return slotRol;
        }

        return primerHuecoInteresado(solicitud);
    }

    private int slotPorDni(Solicitud solicitud, String identificador) {
        String normalizado = TextNormalizer.upperOrNull(identificador);
        if (normalizado == null) {
            return 0;
        }
        if (normalizado.equals(TextNormalizer.upperOrNull(solicitud.getInteresado1Dni()))) {
            return 1;
        }
        if (normalizado.equals(TextNormalizer.upperOrNull(solicitud.getInteresado2Dni()))) {
            return 2;
        }
        if (normalizado.equals(TextNormalizer.upperOrNull(solicitud.getInteresado3Dni()))) {
            return 3;
        }
        return 0;
    }

    private int slotPorRol(Solicitud solicitud, RolInteresado rol) {
        if (rol == solicitud.getInteresado1Rol()) {
            return 1;
        }
        if (rol == solicitud.getInteresado2Rol()) {
            return 2;
        }
        if (rol == solicitud.getInteresado3Rol()) {
            return 3;
        }
        return 0;
    }

    private RolInteresado rolSlot(Solicitud solicitud, int slot) {
        return switch (slot) {
            case 1 -> solicitud.getInteresado1Rol();
            case 2 -> solicitud.getInteresado2Rol();
            case 3 -> solicitud.getInteresado3Rol();
            default -> null;
        };
    }

    private String dniSlot(Solicitud solicitud, int slot) {
        return switch (slot) {
            case 1 -> solicitud.getInteresado1Dni();
            case 2 -> solicitud.getInteresado2Dni();
            case 3 -> solicitud.getInteresado3Dni();
            default -> null;
        };
    }

    private String nombreSlot(Solicitud solicitud, int slot) {
        return switch (slot) {
            case 1 -> solicitud.getInteresado1Nombre();
            case 2 -> solicitud.getInteresado2Nombre();
            case 3 -> solicitud.getInteresado3Nombre();
            default -> null;
        };
    }

    private void aplicarInteresadoHabitual(
            Solicitud solicitud,
            int slot,
            RolInteresado rol,
            Interesado interesado,
            String identificador,
            String nombre
    ) {
        String direccion = direccionSolicitud(
                interesado.getDireccion(),
                interesado.getTipoVia(),
                interesado.getNombreVia(),
                interesado.getCodigoPostal(),
                interesado.getMunicipio(),
                interesado.getProvincia());
        if (slot == 1) {
            solicitud.setInteresado1Rol(rol);
            solicitud.setInteresado1Nombre(nombre);
            solicitud.setInteresado1Dni(identificador);
            solicitud.setInteresado1Telefono(interesado.getTelefono());
            solicitud.setInteresado1Direccion(direccion);
            solicitud.setInteresado1TipoVia(interesado.getTipoVia());
            solicitud.setInteresado1NombreVia(interesado.getNombreVia());
            solicitud.setInteresado1CodigoPostal(interesado.getCodigoPostal());
            solicitud.setInteresado1Municipio(interesado.getMunicipio());
            solicitud.setInteresado1Provincia(interesado.getProvincia());
        } else if (slot == 2) {
            solicitud.setInteresado2Rol(rol);
            solicitud.setInteresado2Nombre(nombre);
            solicitud.setInteresado2Dni(identificador);
            solicitud.setInteresado2Telefono(interesado.getTelefono());
            solicitud.setInteresado2Direccion(direccion);
            solicitud.setInteresado2TipoVia(interesado.getTipoVia());
            solicitud.setInteresado2NombreVia(interesado.getNombreVia());
            solicitud.setInteresado2CodigoPostal(interesado.getCodigoPostal());
            solicitud.setInteresado2Municipio(interesado.getMunicipio());
            solicitud.setInteresado2Provincia(interesado.getProvincia());
        } else {
            solicitud.setInteresado3Rol(rol);
            solicitud.setInteresado3Nombre(nombre);
            solicitud.setInteresado3Dni(identificador);
            solicitud.setInteresado3Telefono(interesado.getTelefono());
            solicitud.setInteresado3Direccion(direccion);
            solicitud.setInteresado3TipoVia(interesado.getTipoVia());
            solicitud.setInteresado3NombreVia(interesado.getNombreVia());
            solicitud.setInteresado3CodigoPostal(interesado.getCodigoPostal());
            solicitud.setInteresado3Municipio(interesado.getMunicipio());
            solicitud.setInteresado3Provincia(interesado.getProvincia());
        }
    }

    private boolean dniYaPresente(Solicitud solicitud, String identificador) {
        String normalizado = TextNormalizer.upperOrNull(identificador);
        return normalizado != null && (
                normalizado.equals(TextNormalizer.upperOrNull(solicitud.getInteresado1Dni()))
                        || normalizado.equals(TextNormalizer.upperOrNull(solicitud.getInteresado2Dni()))
                        || normalizado.equals(TextNormalizer.upperOrNull(solicitud.getInteresado3Dni()))
        );
    }

    private int primerHuecoInteresado(Solicitud solicitud) {
        if (interesadoSolicitudVacio(solicitud.getInteresado1Nombre(), solicitud.getInteresado1Dni(), solicitud.getInteresado1Rol())) {
            return 1;
        }
        if (interesadoSolicitudVacio(solicitud.getInteresado2Nombre(), solicitud.getInteresado2Dni(), solicitud.getInteresado2Rol())) {
            return 2;
        }
        if (interesadoSolicitudVacio(solicitud.getInteresado3Nombre(), solicitud.getInteresado3Dni(), solicitud.getInteresado3Rol())) {
            return 3;
        }
        return 0;
    }

    private void aplicarInteresadoDetectado(
            Solicitud solicitud,
            int slot,
            RolInteresado rol,
            String nombre,
            String identificador,
            String direccion
    ) {
        String direccionNormalizada = TextNormalizer.upperOrNull(direccion);
        if (slot == 1) {
            solicitud.setInteresado1Rol(rol);
            solicitud.setInteresado1Nombre(nombre);
            solicitud.setInteresado1Dni(identificador);
            solicitud.setInteresado1Direccion(direccionNormalizada);
        } else if (slot == 2) {
            solicitud.setInteresado2Rol(rol);
            solicitud.setInteresado2Nombre(nombre);
            solicitud.setInteresado2Dni(identificador);
            solicitud.setInteresado2Direccion(direccionNormalizada);
        } else {
            solicitud.setInteresado3Rol(rol);
            solicitud.setInteresado3Nombre(nombre);
            solicitud.setInteresado3Dni(identificador);
            solicitud.setInteresado3Direccion(direccionNormalizada);
        }
    }

    private String nombreInteresadoDetectado(SolicitudIdentidadDetectadaRequest request, String fallback) {
        String razonSocial = NombrePersonaNormalizer.normalizar(request.getRazonSocial());
        if (razonSocial != null) {
            return razonSocial;
        }
        String nombreCompleto = NombrePersonaNormalizer.normalizar(request.getNombreCompleto());
        if (nombreCompleto != null) {
            return nombreCompleto;
        }
        String joined = String.join(" ",
                List.of(
                        request.getNombre() != null ? request.getNombre() : "",
                        request.getApellido1() != null ? request.getApellido1() : "",
                        request.getApellido2() != null ? request.getApellido2() : ""
                )).replaceAll("\\s+", " ").trim();
        String nombre = NombrePersonaNormalizer.normalizar(joined);
        return nombre != null ? nombre : fallback;
    }

    private void limpiarInteresado1(Solicitud solicitud) {
        solicitud.setInteresado1Rol(null);
        solicitud.setInteresado1Nombre(null);
        solicitud.setInteresado1Dni(null);
        solicitud.setInteresado1Telefono(null);
        solicitud.setInteresado1Direccion(null);
        solicitud.setInteresado1TipoVia(null);
        solicitud.setInteresado1NombreVia(null);
        solicitud.setInteresado1CodigoPostal(null);
        solicitud.setInteresado1Municipio(null);
        solicitud.setInteresado1Provincia(null);
    }

    private void limpiarInteresado2(Solicitud solicitud) {
        solicitud.setInteresado2Rol(null);
        solicitud.setInteresado2Nombre(null);
        solicitud.setInteresado2Dni(null);
        solicitud.setInteresado2Telefono(null);
        solicitud.setInteresado2Direccion(null);
        solicitud.setInteresado2TipoVia(null);
        solicitud.setInteresado2NombreVia(null);
        solicitud.setInteresado2CodigoPostal(null);
        solicitud.setInteresado2Municipio(null);
        solicitud.setInteresado2Provincia(null);
    }

    private void limpiarInteresado3(Solicitud solicitud) {
        solicitud.setInteresado3Rol(null);
        solicitud.setInteresado3Nombre(null);
        solicitud.setInteresado3Dni(null);
        solicitud.setInteresado3Telefono(null);
        solicitud.setInteresado3Direccion(null);
        solicitud.setInteresado3TipoVia(null);
        solicitud.setInteresado3NombreVia(null);
        solicitud.setInteresado3CodigoPostal(null);
        solicitud.setInteresado3Municipio(null);
        solicitud.setInteresado3Provincia(null);
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
        solicitud.setVehiculoMarca(TextNormalizer.upperOrNull(solicitud.getVehiculoMarca()));
        solicitud.setVehiculoModelo(TextNormalizer.upperOrNull(solicitud.getVehiculoModelo()));
        solicitud.setVehiculoBastidor(normalizarCodigoVehiculo(solicitud.getVehiculoBastidor()));
        solicitud.setOperacionPrecioVenta(TextNormalizer.upperOrNull(solicitud.getOperacionPrecioVenta()));
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
        solicitud.setInteresado3Nombre(NombrePersonaNormalizer.normalizar(solicitud.getInteresado3Nombre()));
        solicitud.setInteresado3Dni(TextNormalizer.upperOrNull(solicitud.getInteresado3Dni()));
        solicitud.setInteresado3Telefono(TextNormalizer.upperOrNull(solicitud.getInteresado3Telefono()));
        solicitud.setInteresado3TipoVia(TextNormalizer.upperOrNull(solicitud.getInteresado3TipoVia()));
        solicitud.setInteresado3NombreVia(TextNormalizer.upperOrNull(solicitud.getInteresado3NombreVia()));
        solicitud.setInteresado3CodigoPostal(TextNormalizer.upperOrNull(solicitud.getInteresado3CodigoPostal()));
        solicitud.setInteresado3Municipio(TextNormalizer.upperOrNull(solicitud.getInteresado3Municipio()));
        solicitud.setInteresado3Provincia(TextNormalizer.upperOrNull(solicitud.getInteresado3Provincia()));
        solicitud.setInteresado3Direccion(direccionSolicitud(
                solicitud.getInteresado3Direccion(),
                solicitud.getInteresado3TipoVia(),
                solicitud.getInteresado3NombreVia(),
                solicitud.getInteresado3CodigoPostal(),
                solicitud.getInteresado3Municipio(),
                solicitud.getInteresado3Provincia()));
    }

    private String direccionSolicitud(String direccion, String tipoVia, String nombreVia, String codigoPostal, String municipio, String provincia) {
        String direccionNormalizada = TextNormalizer.upperOrNull(direccion);
        if (direccionNormalizada != null) {
            return direccionNormalizada;
        }
        return DireccionFormatter.componer(tipoVia, nombreVia, codigoPostal, municipio, provincia);
    }

    private void completarVehiculoDesdeSolicitud(Vehiculo vehiculo, Solicitud solicitud) {
        if (vehiculo == null) {
            return;
        }
        String bastidor = normalizarCodigoVehiculo(solicitud.getVehiculoBastidor());
        String marca = TextNormalizer.upperOrNull(solicitud.getVehiculoMarca());
        String modelo = TextNormalizer.upperOrNull(solicitud.getVehiculoModelo());
        if (bastidor != null) {
            vehiculo.setBastidor(bastidor);
        }
        if (marca != null) {
            vehiculo.setMarca(marca);
        }
        if (modelo != null) {
            vehiculo.setModelo(modelo);
        }
    }

    private String normalizarCodigoVehiculo(String value) {
        String normalizado = TextNormalizer.upperOrNull(value);
        if (normalizado == null) {
            return null;
        }
        normalizado = normalizado.replaceAll("[^A-Z0-9]", "");
        return normalizado.isBlank() ? null : normalizado;
    }
}

