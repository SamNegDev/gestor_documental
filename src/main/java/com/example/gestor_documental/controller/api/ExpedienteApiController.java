package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.dto.expediente.ActualizarExpedienteRequest;
import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteEditCatalogsResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteListItemResponse;
import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.dto.expediente.ListCatalogsResponse;
import com.example.gestor_documental.dto.expediente.TipoIncidenciaResponse;
import com.example.gestor_documental.dto.expediente.TipoTramiteResumenResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HitoExpedienteService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.TipoIncidenciaService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/expedientes")
@RequiredArgsConstructor
public class ExpedienteApiController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final ExpedienteService expedienteService;
    private final HitoExpedienteService hitoExpedienteService;
    private final MensajeService mensajeService;
    private final ClienteService clienteService;
    private final TipoIncidenciaService tipoIncidenciaService;
    private final TipoTramiteService tipoTramiteService;
    private final UsuarioService usuarioService;

    @GetMapping
    public List<ExpedienteListItemResponse> listarExpedientes(
            Authentication authentication,
            @RequestParam(required = false) EstadoExpediente estado,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false, defaultValue = "ESTE_MES") String periodo
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        List<Expediente> expedientes;

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            expedientes = expedienteService.listarTodos();
            if (clienteId != null) {
                expedientes = expedientes.stream()
                        .filter(expediente -> expediente.getCliente() != null && expediente.getCliente().getId().equals(clienteId))
                        .toList();
            }
        } else if (usuarioLogueado.getCliente() != null) {
            expedientes = expedienteService.listarPorClienteId(usuarioLogueado.getCliente().getId());
        } else {
            expedientes = List.of();
        }

        if (estado != null) {
            expedientes = expedientes.stream()
                    .filter(expediente -> expediente.getEstadoExpediente() == estado)
                    .toList();
        }
        if (tipoTramiteId != null) {
            expedientes = expedientes.stream()
                    .filter(expediente -> expediente.getTipoTramite() != null && expediente.getTipoTramite().getId().equals(tipoTramiteId))
                    .toList();
        }
        if (matricula != null && !matricula.trim().isEmpty()) {
            String busqueda = matricula.trim().toLowerCase();
            expedientes = expedientes.stream()
                    .filter(expediente -> expediente.getMatricula() != null && expediente.getMatricula().toLowerCase().contains(busqueda))
                    .toList();
        }
        DateRange dateRange = dateRange(periodo);
        if (dateRange != null) {
            expedientes = expedientes.stream()
                    .filter(expediente -> isWithinRange(expediente.getFechaCreacion(), dateRange))
                    .toList();
        }

        return expedientes.stream().map(this::mapExpedienteListItem).toList();
    }

    @GetMapping("/catalogos-listado")
    public ListCatalogsResponse obtenerCatalogosListado(Authentication authentication) {
        Usuario usuarioLogueado = usuario(authentication);
        return ListCatalogsResponse.builder()
                .estados(Arrays.stream(EstadoExpediente.values()).map(Enum::name).toList())
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
    public ExpedienteDetailResponse obtenerDetalle(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        return expedienteDetalleApiService.obtenerDetalle(id, usuarioLogueado);
    }

    @GetMapping("/catalogos-edicion")
    public ExpedienteEditCatalogsResponse obtenerCatalogosEdicion(Authentication authentication) {
        requireAdmin(authentication);
        return ExpedienteEditCatalogsResponse.builder()
                .clientes(clienteService.listarTodos().stream()
                        .map(cliente -> ClienteResumenResponse.builder()
                                .id(cliente.getId())
                                .nombre(cliente.getNombre())
                                .nif(cliente.getNif())
                                .email(cliente.getEmail())
                                .telefono(cliente.getTelefono())
                                .build())
                        .toList())
                .tiposTramite(tipoTramiteService.listarTodos().stream()
                        .filter(tipo -> tipo.isActivo())
                        .map(tipo -> TipoTramiteResumenResponse.builder()
                                .id(tipo.getId())
                                .nombre(tipo.getNombre() != null ? tipo.getNombre().name() : null)
                                .descripcion(tipo.getDescripcion())
                                .build())
                        .toList())
                .build();
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crearExpediente(
            @RequestBody ActualizarExpedienteRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        validarDatosBase(request);

        Expediente expediente = new Expediente();
        expediente.setMatricula(request.getMatricula());
        expediente.setObservaciones(request.getObservaciones());

        Expediente creado = expedienteService.crearExpedienteCompleto(
                expediente,
                usuarioLogueado,
                request.getClienteId(),
                request.getTipoTramiteId(),
                mapInteresados(request)
        );

        return ResponseEntity
                .created(URI.create("/expedientes/" + creado.getId()))
                .body(Map.of("id", creado.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizarExpediente(
            @PathVariable Long id,
            @RequestBody ActualizarExpedienteRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        validarDatosBase(request);
        Expediente expedienteActualizado = new Expediente();
        expedienteActualizado.setMatricula(request.getMatricula());
        expedienteActualizado.setObservaciones(request.getObservaciones());

        expedienteService.actualizarExpediente(
                id,
                expedienteActualizado,
                usuarioLogueado,
                request.getClienteId(),
                request.getTipoTramiteId(),
                mapInteresados(request)
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/hitos/{codigo}/completar")
    public ResponseEntity<Void> completarHito(
            @PathVariable Long id,
            @PathVariable CodigoHitoExpediente codigo,
            Authentication authentication
    ) {
        hitoExpedienteService.completar(id, codigo, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<Void> finalizar(@PathVariable Long id, Authentication authentication) {
        hitoExpedienteService.finalizar(id, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/incidencia")
    public ResponseEntity<Void> abrirIncidencia(
            @PathVariable Long id,
            @RequestParam Long tipoIncidenciaId,
            @RequestParam(required = false) String observaciones,
            Authentication authentication
    ) {
        hitoExpedienteService.abrirIncidencia(id, tipoIncidenciaId, observaciones, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mensajes")
    public ResponseEntity<Void> añadirMensaje(
            @PathVariable Long id,
            @RequestParam(required = false) String contenido,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication authentication
    ) {
        String contenidoFinal = contenido != null ? contenido : body != null ? body.get("contenido") : null;
        mensajeService.añadirAExpediente(id, contenidoFinal, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tipos-incidencia")
    public java.util.List<TipoIncidenciaResponse> listarTiposIncidencia() {
        return tipoIncidenciaService.listarTodosActivos().stream()
                .map(tipo -> TipoIncidenciaResponse.builder()
                        .id(tipo.getId())
                        .nombre(tipo.getNombre() != null ? tipo.getNombre().name() : null)
                        .descripcion(tipo.getDescripcion())
                        .build())
                .toList();
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

    private void validarDatosBase(ActualizarExpedienteRequest request) {
        if (request.getClienteId() == null || request.getTipoTramiteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona cliente y tipo de tramite");
        }
    }

    private InteresadoFormDto mapInteresado(ActualizarExpedienteRequest request, int index) {
        if (request.getInteresados() == null || request.getInteresados().size() <= index) {
            return new InteresadoFormDto();
        }
        var source = request.getInteresados().get(index);
        InteresadoFormDto dto = new InteresadoFormDto();
        dto.setNombre(source.getNombre());
        dto.setDni(source.getDni());
        dto.setTelefono(source.getTelefono());
        dto.setDireccion(source.getDireccion());
        dto.setRol(source.getRol());
        return dto;
    }

    private List<InteresadoFormDto> mapInteresados(ActualizarExpedienteRequest request) {
        if (request.getInteresados() == null || request.getInteresados().isEmpty()) {
            return List.of(mapInteresado(request, 0), mapInteresado(request, 1));
        }
        return request.getInteresados().stream().map(interesado -> {
            InteresadoFormDto dto = new InteresadoFormDto();
            dto.setNombre(interesado.getNombre());
            dto.setDni(interesado.getDni());
            dto.setTelefono(interesado.getTelefono());
            dto.setDireccion(interesado.getDireccion());
            dto.setRol(interesado.getRol());
            return dto;
        }).toList();
    }

    private ExpedienteListItemResponse mapExpedienteListItem(Expediente expediente) {
        return ExpedienteListItemResponse.builder()
                .id(expediente.getId())
                .matricula(expediente.getMatricula())
                .tipoTramite(expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                        ? expediente.getTipoTramite().getNombre().name()
                        : null)
                .estado(expediente.getEstadoExpediente() != null ? expediente.getEstadoExpediente().name() : null)
                .fechaCreacion(formatDate(expediente.getFechaCreacion()))
                .fechaUltimaModificacion(formatDate(expediente.getFechaUltimaModificacion()))
                .cliente(expediente.getCliente() != null
                        ? ClienteResumenResponse.builder()
                                .id(expediente.getCliente().getId())
                                .nombre(expediente.getCliente().getNombre())
                                .nif(expediente.getCliente().getNif())
                                .email(expediente.getCliente().getEmail())
                                .telefono(expediente.getCliente().getTelefono())
                                .build()
                        : null)
                .modificadoPor(mapUsuario(expediente.getModificadoPor()))
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
