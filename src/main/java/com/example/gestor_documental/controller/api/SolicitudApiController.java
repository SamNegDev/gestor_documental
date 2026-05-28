package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.DocumentoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.HistorialExpedienteResponse;
import com.example.gestor_documental.dto.expediente.IncidenciaExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InteresadoSolicitudResponse;
import com.example.gestor_documental.dto.expediente.ListCatalogsResponse;
import com.example.gestor_documental.dto.expediente.MensajeExpedienteResponse;
import com.example.gestor_documental.dto.expediente.SolicitudUpsertRequest;
import com.example.gestor_documental.dto.expediente.SolicitudDetailResponse;
import com.example.gestor_documental.dto.expediente.SolicitudListItemResponse;
import com.example.gestor_documental.dto.expediente.TipoTramiteResumenResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
public class SolicitudApiController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SolicitudService solicitudService;
    private final UsuarioService usuarioService;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;
    private final DocumentoService documentoService;
    private final IncidenciaService incidenciaService;
    private final HistorialCambioService historialCambioService;
    private final MensajeService mensajeService;

    @GetMapping
    public List<SolicitudListItemResponse> listarSolicitudes(
            Authentication authentication,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false, defaultValue = "ESTE_MES") String periodo
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        List<Solicitud> solicitudes;

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            solicitudes = solicitudService.listarTodas();
            if (clienteId != null) {
                solicitudes = solicitudes.stream()
                        .filter(solicitud -> solicitud.getCliente() != null && solicitud.getCliente().getId().equals(clienteId))
                        .toList();
            }
        } else if (usuarioLogueado.getCliente() != null) {
            solicitudes = solicitudService.listarPorClienteId(usuarioLogueado.getCliente().getId());
        } else {
            solicitudes = List.of();
        }

        if (estado != null) {
            solicitudes = solicitudes.stream()
                    .filter(solicitud -> solicitud.getEstadoSolicitud() == estado)
                    .toList();
        }
        if (tipoTramiteId != null) {
            solicitudes = solicitudes.stream()
                    .filter(solicitud -> solicitud.getTipoTramite() != null && solicitud.getTipoTramite().getId().equals(tipoTramiteId))
                    .toList();
        }
        if (matricula != null && !matricula.trim().isEmpty()) {
            String busqueda = matricula.trim().toLowerCase();
            solicitudes = solicitudes.stream()
                    .filter(solicitud -> solicitud.getMatricula() != null && solicitud.getMatricula().toLowerCase().contains(busqueda))
                    .toList();
        }
        DateRange dateRange = dateRange(periodo);
        if (dateRange != null) {
            solicitudes = solicitudes.stream()
                    .filter(solicitud -> isWithinRange(solicitud.getFechaCreacion(), dateRange))
                    .toList();
        }

        return solicitudes.stream().map(this::mapSolicitudListItem).toList();
    }

    @GetMapping("/catalogos-listado")
    public ListCatalogsResponse obtenerCatalogosListado(Authentication authentication) {
        Usuario usuarioLogueado = usuario(authentication);
        return ListCatalogsResponse.builder()
                .estados(Arrays.stream(EstadoSolicitud.values()).map(Enum::name).toList())
                .tiposTramite(tipoTramiteService.listarTodos().stream()
                        .map(tipo -> TipoTramiteResumenResponse.builder()
                                .id(tipo.getId())
                                .nombre(tipo.getNombre() != null ? tipo.getNombre().name() : null)
                                .descripcion(tipo.getDescripcion())
                                .build())
                        .toList())
                .clientes(usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN
                        ? clienteService.listarTodos().stream()
                                .map(cliente -> ClienteResumenResponse.builder()
                                        .id(cliente.getId())
                                        .nombre(cliente.getNombre())
                                        .nif(cliente.getNif())
                                        .email(cliente.getEmail())
                                        .telefono(cliente.getTelefono())
                                        .build())
                                .toList()
                        : List.of())
                .build();
    }

    @GetMapping("/{id}")
    public SolicitudDetailResponse obtenerDetalle(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = usuario(authentication);
        Solicitud solicitud = solicitudService.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!solicitudService.tienePermisoSolicitud(solicitud, usuarioLogueado)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para acceder a esta solicitud");
        }

        return mapSolicitudDetail(solicitud);
    }

    @PostMapping("/{id}/convertir")
    public ResponseEntity<java.util.Map<String, Long>> convertir(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        Expediente expediente = solicitudService.convertirAExpediente(id, usuarioLogueado);
        return ResponseEntity.ok(java.util.Map.of("id", expediente.getId()));
    }

    @PostMapping
    public ResponseEntity<java.util.Map<String, Long>> crear(
            @RequestBody SolicitudUpsertRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        if (usuarioLogueado.getCliente() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes un cliente asociado");
        }
        validarSolicitudRequest(request);
        Solicitud creada = solicitudService.crearSolicitudCompleta(
                mapSolicitudRequest(request),
                usuarioLogueado,
                usuarioLogueado.getCliente(),
                request.getTipoTramiteId()
        );
        return ResponseEntity.ok(java.util.Map.of("id", creada.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(
            @PathVariable Long id,
            @RequestBody SolicitudUpsertRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        validarSolicitudRequest(request);
        solicitudService.actualizarSolicitud(id, mapSolicitudRequest(request), usuarioLogueado, request.getTipoTramiteId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        EstadoSolicitud estado = EstadoSolicitud.valueOf(body.get("nuevoEstado"));
        solicitudService.cambiarEstadoSolicitud(id, estado, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Void> anadirMensaje(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        mensajeService.añadirASolicitud(id, body.get("contenido"), usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documentos")
    public ResponseEntity<Void> subirDocumento(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        documentoService.guardarParaSolicitud(id, archivo, tipoDocumento, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    private Usuario usuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName());
    }

    private Usuario requireAdmin(Authentication authentication) {
        Usuario usuario = usuario(authentication);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede realizar esta accion");
        }
        return usuario;
    }

    private void validarSolicitudRequest(SolicitudUpsertRequest request) {
        if (request.getTipoTramiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un tipo de tramite");
        }
        if (request.getMatricula() == null || request.getMatricula().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La matricula es obligatoria");
        }
    }

    private Solicitud mapSolicitudRequest(SolicitudUpsertRequest request) {
        Solicitud solicitud = new Solicitud();
        solicitud.setMatricula(clean(request.getMatricula()));
        solicitud.setObservaciones(clean(request.getObservaciones()));
        solicitud.setInteresado1Rol(request.getInteresado1Rol());
        solicitud.setInteresado1Nombre(clean(request.getInteresado1Nombre()));
        solicitud.setInteresado1Apellidos(clean(request.getInteresado1Apellidos()));
        solicitud.setInteresado1Dni(clean(request.getInteresado1Dni()));
        solicitud.setInteresado1Telefono(clean(request.getInteresado1Telefono()));
        solicitud.setInteresado1Direccion(clean(request.getInteresado1Direccion()));
        solicitud.setInteresado2Rol(request.getInteresado2Rol());
        solicitud.setInteresado2Nombre(clean(request.getInteresado2Nombre()));
        solicitud.setInteresado2Apellidos(clean(request.getInteresado2Apellidos()));
        solicitud.setInteresado2Dni(clean(request.getInteresado2Dni()));
        solicitud.setInteresado2Telefono(clean(request.getInteresado2Telefono()));
        solicitud.setInteresado2Direccion(clean(request.getInteresado2Direccion()));
        return solicitud;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SolicitudListItemResponse mapSolicitudListItem(Solicitud solicitud) {
        return SolicitudListItemResponse.builder()
                .id(solicitud.getId())
                .matricula(solicitud.getMatricula())
                .tipoTramite(solicitud.getTipoTramite() != null && solicitud.getTipoTramite().getNombre() != null
                        ? solicitud.getTipoTramite().getNombre().name()
                        : null)
                .estado(solicitud.getEstadoSolicitud() != null ? solicitud.getEstadoSolicitud().name() : null)
                .fechaCreacion(formatDate(solicitud.getFechaCreacion()))
                .fechaUltimaModificacion(formatDate(solicitud.getFechaUltimaModificacion()))
                .cliente(solicitud.getCliente() != null
                        ? ClienteResumenResponse.builder()
                                .id(solicitud.getCliente().getId())
                                .nombre(solicitud.getCliente().getNombre())
                                .nif(solicitud.getCliente().getNif())
                                .email(solicitud.getCliente().getEmail())
                                .telefono(solicitud.getCliente().getTelefono())
                                .build()
                        : null)
                .modificadoPor(mapUsuario(solicitud.getModificadoPor()))
                .expedienteId(solicitud.getExpediente() != null ? solicitud.getExpediente().getId() : null)
                .build();
    }

    private UsuarioResumenResponse mapUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String nombreCompleto = (nombre + " " + apellidos).trim();
        return UsuarioResumenResponse.builder()
                .id(usuario.getId())
                .nombreCompleto(!nombreCompleto.isEmpty() ? nombreCompleto : usuario.getEmail())
                .email(usuario.getEmail())
                .rol(usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null)
                .build();
    }

    private String formatDate(LocalDateTime fecha) {
        return fecha != null ? fecha.format(DATE_TIME_FORMATTER) : null;
    }

    private SolicitudDetailResponse mapSolicitudDetail(Solicitud solicitud) {
        return SolicitudDetailResponse.builder()
                .id(solicitud.getId())
                .matricula(solicitud.getMatricula())
                .tipoTramite(solicitud.getTipoTramite() != null && solicitud.getTipoTramite().getNombre() != null
                        ? solicitud.getTipoTramite().getNombre().name()
                        : null)
                .estado(solicitud.getEstadoSolicitud() != null ? solicitud.getEstadoSolicitud().name() : null)
                .fechaCreacion(formatDate(solicitud.getFechaCreacion()))
                .fechaUltimaModificacion(formatDate(solicitud.getFechaUltimaModificacion()))
                .observaciones(solicitud.getObservaciones())
                .expedienteId(solicitud.getExpediente() != null ? solicitud.getExpediente().getId() : null)
                .cliente(solicitud.getCliente() != null
                        ? ClienteResumenResponse.builder()
                                .id(solicitud.getCliente().getId())
                                .nombre(solicitud.getCliente().getNombre())
                                .nif(solicitud.getCliente().getNif())
                                .email(solicitud.getCliente().getEmail())
                                .telefono(solicitud.getCliente().getTelefono())
                                .build()
                        : null)
                .creadoPor(mapUsuario(solicitud.getCreadoPor()))
                .modificadoPor(mapUsuario(solicitud.getModificadoPor()))
                .interesados(mapInteresados(solicitud))
                .documentos(documentoService.listarPorSolicitud(solicitud.getId()).stream().map(this::mapDocumento).toList())
                .incidencias(incidenciaService.listarPorSolicitud(solicitud.getId()).stream().map(this::mapIncidencia).toList())
                .historial(historialCambioService.listarPorSolicitud(solicitud.getId()).stream()
                        .filter(cambio -> !"CARGAR DOCUMENTO".equals(cambio.getAccion()))
                        .map(this::mapHistorial)
                        .toList())
                .mensajes(mensajeService.listarPorSolicitud(solicitud.getId()).stream().map(this::mapMensaje).toList())
                .build();
    }

    private List<InteresadoSolicitudResponse> mapInteresados(Solicitud solicitud) {
        java.util.ArrayList<InteresadoSolicitudResponse> interesados = new java.util.ArrayList<>();
        if (hasInteresadoData(
                solicitud.getInteresado1Nombre(),
                solicitud.getInteresado1Apellidos(),
                solicitud.getInteresado1Dni(),
                solicitud.getInteresado1Telefono(),
                solicitud.getInteresado1Direccion(),
                solicitud.getInteresado1Rol() != null ? solicitud.getInteresado1Rol().name() : null)) {
            interesados.add(InteresadoSolicitudResponse.builder()
                    .nombre(solicitud.getInteresado1Nombre())
                    .apellidos(solicitud.getInteresado1Apellidos())
                    .rol(solicitud.getInteresado1Rol() != null ? solicitud.getInteresado1Rol().name() : null)
                    .dni(solicitud.getInteresado1Dni())
                    .telefono(solicitud.getInteresado1Telefono())
                    .direccion(solicitud.getInteresado1Direccion())
                    .build());
        }
        if (hasInteresadoData(
                solicitud.getInteresado2Nombre(),
                solicitud.getInteresado2Apellidos(),
                solicitud.getInteresado2Dni(),
                solicitud.getInteresado2Telefono(),
                solicitud.getInteresado2Direccion(),
                solicitud.getInteresado2Rol() != null ? solicitud.getInteresado2Rol().name() : null)) {
            interesados.add(InteresadoSolicitudResponse.builder()
                    .nombre(solicitud.getInteresado2Nombre())
                    .apellidos(solicitud.getInteresado2Apellidos())
                    .rol(solicitud.getInteresado2Rol() != null ? solicitud.getInteresado2Rol().name() : null)
                    .dni(solicitud.getInteresado2Dni())
                    .telefono(solicitud.getInteresado2Telefono())
                    .direccion(solicitud.getInteresado2Direccion())
                    .build());
        }
        return interesados;
    }

    private boolean hasInteresadoData(String... values) {
        return java.util.Arrays.stream(values).anyMatch(value -> value != null && !value.trim().isEmpty());
    }

    private DocumentoExpedienteResponse mapDocumento(Documento documento) {
        return DocumentoExpedienteResponse.builder()
                .id(documento.getId())
                .nombre(documento.getNombreArchivo())
                .nombreOriginal(documento.getNombreArchivoOriginal())
                .tipo(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : null)
                .descripcion(documento.getDescripcionArchivo())
                .fechaSubida(formatDate(documento.getFechaSubida()))
                .subidoPor(documento.getSubidoPor() != null ? nombreCompleto(documento.getSubidoPor()) : null)
                .estado("SUBIDO")
                .subido(true)
                .requeridoAhora(false)
                .build();
    }

    private IncidenciaExpedienteResponse mapIncidencia(Incidencia incidencia) {
        return IncidenciaExpedienteResponse.builder()
                .id(incidencia.getId())
                .tipo(incidencia.getTipoIncidencia() != null && incidencia.getTipoIncidencia().getNombre() != null
                        ? incidencia.getTipoIncidencia().getNombre().name()
                        : null)
                .observaciones(incidencia.getObservaciones())
                .fechaCreacion(formatDate(incidencia.getFechaCreacion()))
                .resuelta(incidencia.isResuelta())
                .fechaResolucion(formatDate(incidencia.getFechaResolucion()))
                .creadoPor(incidencia.getCreadoPor() != null ? nombreCompleto(incidencia.getCreadoPor()) : null)
                .resueltoPor(incidencia.getResueltoPor() != null ? nombreCompleto(incidencia.getResueltoPor()) : null)
                .build();
    }

    private HistorialExpedienteResponse mapHistorial(HistorialCambio cambio) {
        return HistorialExpedienteResponse.builder()
                .id(cambio.getId())
                .accion(cambio.getAccion())
                .descripcion(cambio.getDescripcion())
                .fechaCambio(formatDate(cambio.getFechaCambio()))
                .usuario(cambio.getUsuario() != null ? nombreCompleto(cambio.getUsuario()) : "Sistema")
                .build();
    }

    private MensajeExpedienteResponse mapMensaje(Mensaje mensaje) {
        return MensajeExpedienteResponse.builder()
                .id(mensaje.getId())
                .autor(mensaje.getAutor() != null ? nombreCompleto(mensaje.getAutor()) : "Sistema")
                .rolAutor(mensaje.getAutor() != null && mensaje.getAutor().getRolUsuario() != null
                        ? mensaje.getAutor().getRolUsuario().name()
                        : null)
                .fechaCreacion(formatDate(mensaje.getFechaCreacion()))
                .contenido(mensaje.getContenido())
                .build();
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String nombreCompleto = (nombre + " " + apellidos).trim();
        return !nombreCompleto.isEmpty() ? nombreCompleto : usuario.getEmail();
    }

    private DateRange dateRange(String periodo) {
        LocalDate today = LocalDate.now();
        return switch (periodo != null ? periodo : "ESTE_MES") {
            case "ULTIMOS_3_MESES" -> new DateRange(today.minusMonths(3).atStartOfDay(), null);
            case "ESTE_ANIO" -> new DateRange(today.withDayOfYear(1).atStartOfDay(), null);
            case "TODO" -> null;
            default -> new DateRange(today.withDayOfMonth(1).atStartOfDay(), null);
        };
    }

    private boolean isWithinRange(LocalDateTime fecha, DateRange range) {
        if (fecha == null) {
            return false;
        }
        boolean afterStart = !fecha.isBefore(range.start());
        boolean beforeEnd = range.end() == null || fecha.isBefore(range.end());
        return afterStart && beforeEnd;
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }
}
