package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;

import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.DocumentoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.HistorialExpedienteResponse;
import com.example.gestor_documental.dto.expediente.IncidenciaExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InteresadoSolicitudResponse;
import com.example.gestor_documental.dto.expediente.ListCatalogsResponse;
import com.example.gestor_documental.dto.expediente.MensajeExpedienteResponse;
import com.example.gestor_documental.dto.expediente.SolicitudUpsertRequest;
import com.example.gestor_documental.dto.expediente.SolicitudBulkConvertRequest;
import com.example.gestor_documental.dto.expediente.SolicitudBulkConvertResponse;
import com.example.gestor_documental.dto.expediente.SolicitudBulkConvertResponse.SolicitudBulkConvertResult;
import com.example.gestor_documental.dto.expediente.SolicitudDetailResponse;
import com.example.gestor_documental.dto.expediente.SolicitudInteresadoCoincidenciaResponse;
import com.example.gestor_documental.dto.expediente.SolicitudListItemResponse;
import com.example.gestor_documental.dto.expediente.TipoTramiteResumenResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
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
import com.example.gestor_documental.service.impl.CorreoEntranteSolicitudService;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.util.TextNormalizer;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;
    private final DocumentoService documentoService;
    private final IncidenciaService incidenciaService;
    private final HistorialCambioService historialCambioService;
    private final MensajeService mensajeService;
    private final CurrentUserService currentUserService;
    private final CorreoEntranteSolicitudService correoEntranteSolicitudService;

    @GetMapping
    public PagedResponse<SolicitudListItemResponse> listarSolicitudes(
            Authentication authentication,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false, defaultValue = "ESTE_MES") String periodo
            , @RequestParam(required = false) LocalDate fechaDesde
            , @RequestParam(required = false) LocalDate fechaHasta
            , @RequestParam(required = false, defaultValue = "0") int pagina
            , @RequestParam(required = false, defaultValue = "25") int tamanio
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        Long clienteVisibleId = usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN
                ? clienteId
                : usuarioLogueado.getCliente() != null ? usuarioLogueado.getCliente().getId() : null;
        if (usuarioLogueado.getRolUsuario() != RolUsuario.ADMIN && clienteVisibleId == null) {
            return PagedResponse.of(List.of(), pagina, tamanio);
        }
        DateRange dateRange = dateRange(periodo, fechaDesde, fechaHasta);
        return PagedResponse.of(solicitudService.buscarListado(
                        clienteVisibleId,
                        estado,
                        tipoTramiteId,
                        likeParam(matricula),
                        dateRange != null ? dateRange.desde() : null,
                        dateRange != null ? dateRange.hasta() : null,
                        pageRequest(pagina, tamanio)
                )
                .map(this::mapSolicitudListItem));
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

    @GetMapping("/{id}/interesados/coincidencias")
    public List<SolicitudInteresadoCoincidenciaResponse> revisarCoincidenciasInteresados(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        return solicitudService.buscarCoincidenciasInteresadosConDiferencias(id, usuarioLogueado);
    }

    @PostMapping("/convertir-masivo")
    public ResponseEntity<SolicitudBulkConvertResponse> convertirMasivo(
            @RequestBody SolicitudBulkConvertRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        List<Long> ids = request.getSolicitudIds() != null ? request.getSolicitudIds().stream().distinct().toList() : List.of();
        List<SolicitudBulkConvertResult> resultados = new java.util.ArrayList<>();
        for (Long solicitudId : ids) {
            try {
                Expediente expediente = solicitudService.convertirAExpediente(solicitudId, usuarioLogueado);
                resultados.add(SolicitudBulkConvertResult.builder()
                        .solicitudId(solicitudId)
                        .expedienteId(expediente.getId())
                        .convertida(true)
                        .mensaje("Convertida")
                        .build());
            } catch (Exception ex) {
                resultados.add(SolicitudBulkConvertResult.builder()
                        .solicitudId(solicitudId)
                        .convertida(false)
                        .mensaje(ex.getMessage() != null ? ex.getMessage() : "No se pudo convertir")
                        .build());
            }
        }
        int convertidas = (int) resultados.stream().filter(SolicitudBulkConvertResult::isConvertida).count();
        return ResponseEntity.ok(SolicitudBulkConvertResponse.builder()
                .total(resultados.size())
                .convertidas(convertidas)
                .fallidas(resultados.size() - convertidas)
                .resultados(resultados)
                .build());
    }

    @PostMapping("/correo-entrante/comprobar")
    public ResponseEntity<java.util.Map<String, Object>> comprobarCorreoEntrante(Authentication authentication) {
        requireAdmin(authentication);
        boolean ejecutada = correoEntranteSolicitudService.intentarProcesarBuzon();
        return ResponseEntity.ok(java.util.Map.of(
                "ejecutada", ejecutada,
                "mensaje", ejecutada
                        ? "Comprobacion completada."
                        : "La comprobacion no se ejecuto porque el buzon esta desactivado o ya hay otra comprobacion en curso."
        ));
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
        return currentUserService.requireUser(authentication);
    }

    private Usuario requireAdmin(Authentication authentication) {
        return currentUserService.requireAdmin(authentication);
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
        solicitud.setMatricula(TextNormalizer.upperOrNull(request.getMatricula()));
        solicitud.setObservaciones(TextNormalizer.upperOrNull(request.getObservaciones()));
        solicitud.setInteresado1Rol(request.getInteresado1Rol());
        solicitud.setInteresado1Nombre(TextNormalizer.upperOrNull(request.getInteresado1Nombre()));
        solicitud.setInteresado1Dni(TextNormalizer.upperOrNull(request.getInteresado1Dni()));
        solicitud.setInteresado1Telefono(TextNormalizer.upperOrNull(request.getInteresado1Telefono()));
        solicitud.setInteresado1Direccion(TextNormalizer.upperOrNull(request.getInteresado1Direccion()));
        solicitud.setInteresado1TipoVia(TextNormalizer.upperOrNull(request.getInteresado1TipoVia()));
        solicitud.setInteresado1NombreVia(TextNormalizer.upperOrNull(request.getInteresado1NombreVia()));
        solicitud.setInteresado1CodigoPostal(TextNormalizer.upperOrNull(request.getInteresado1CodigoPostal()));
        solicitud.setInteresado1Municipio(TextNormalizer.upperOrNull(request.getInteresado1Municipio()));
        solicitud.setInteresado1Provincia(TextNormalizer.upperOrNull(request.getInteresado1Provincia()));
        solicitud.setInteresado2Rol(request.getInteresado2Rol());
        solicitud.setInteresado2Nombre(TextNormalizer.upperOrNull(request.getInteresado2Nombre()));
        solicitud.setInteresado2Dni(TextNormalizer.upperOrNull(request.getInteresado2Dni()));
        solicitud.setInteresado2Telefono(TextNormalizer.upperOrNull(request.getInteresado2Telefono()));
        solicitud.setInteresado2Direccion(TextNormalizer.upperOrNull(request.getInteresado2Direccion()));
        solicitud.setInteresado2TipoVia(TextNormalizer.upperOrNull(request.getInteresado2TipoVia()));
        solicitud.setInteresado2NombreVia(TextNormalizer.upperOrNull(request.getInteresado2NombreVia()));
        solicitud.setInteresado2CodigoPostal(TextNormalizer.upperOrNull(request.getInteresado2CodigoPostal()));
        solicitud.setInteresado2Municipio(TextNormalizer.upperOrNull(request.getInteresado2Municipio()));
        solicitud.setInteresado2Provincia(TextNormalizer.upperOrNull(request.getInteresado2Provincia()));
        return solicitud;
    }

    private SolicitudListItemResponse mapSolicitudListItem(Solicitud solicitud) {
        List<Documento> documentos = documentoService.listarPorSolicitud(solicitud.getId());
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
                .interesados(mapInteresados(solicitud))
                .situacionDocumental(situacionDocumental(solicitud, documentos))
                .build();
    }

    private String situacionDocumental(Solicitud solicitud, List<Documento> documentos) {
        if (documentos.isEmpty()) return "SIN DOCUMENTACION";
        int pendientes = contarDocumentosBasicosPendientes(solicitud, documentos);
        if (pendientes > 0) return pendientes == 1 ? "FALTA 1 DOCUMENTO" : "FALTAN " + pendientes + " DOCUMENTOS";
        return "DOCUMENTACION COMPLETA";
    }

    private int contarDocumentosBasicosPendientes(Solicitud solicitud, List<Documento> documentos) {
        Set<TipoDocumento> tipos = documentos.stream().map(Documento::getTipoDocumento).collect(Collectors.toSet());
        int pendientes = 0;
        boolean tramiteVenta = solicitud.getTipoTramite() != null
                && (solicitud.getTipoTramite().getNombre() == TipoTramiteEnum.TRASPASO
                || solicitud.getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM);
        boolean tieneInteresadoAplicable = tramiteVenta
                ? esRolVenta(solicitud.getInteresado1Rol()) || esRolVenta(solicitud.getInteresado2Rol())
                : solicitud.getInteresado1Rol() == RolInteresado.TITULAR
                || solicitud.getInteresado2Rol() == RolInteresado.TITULAR;

        if (tieneInteresadoAplicable && !tipos.contains(TipoDocumento.DNI) && !tipos.contains(TipoDocumento.CIF)) pendientes++;
        if (tramiteVenta && !tipos.contains(TipoDocumento.CONTRATO_COMPRAVENTA) && !tipos.contains(TipoDocumento.FACTURA)) pendientes++;
        if (!tipos.contains(TipoDocumento.MANDATO) && !tipos.contains(TipoDocumento.MANDATO_REPRESENTACION)) pendientes++;
        return pendientes;
    }

    private boolean esRolVenta(RolInteresado rol) {
        return rol == RolInteresado.COMPRADOR
                || rol == RolInteresado.VENDEDOR
                || rol == RolInteresado.COMPRAVENTA;
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

    private PageRequest pageRequest(int pagina, int tamanio) {
        return PageRequest.of(Math.max(0, pagina), Math.max(1, Math.min(tamanio, 100)));
    }

    private String likeParam(String valor) {
        return valor != null && !valor.trim().isEmpty()
                ? "%" + valor.trim().toUpperCase() + "%"
                : null;
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
                solicitud.getInteresado1Dni(),
                solicitud.getInteresado1Telefono(),
                solicitud.getInteresado1Direccion(),
                solicitud.getInteresado1TipoVia(),
                solicitud.getInteresado1NombreVia(),
                solicitud.getInteresado1CodigoPostal(),
                solicitud.getInteresado1Municipio(),
                solicitud.getInteresado1Provincia(),
                solicitud.getInteresado1Rol() != null ? solicitud.getInteresado1Rol().name() : null)) {
            interesados.add(InteresadoSolicitudResponse.builder()
                    .nombre(solicitud.getInteresado1Nombre())
                    .rol(solicitud.getInteresado1Rol() != null ? solicitud.getInteresado1Rol().name() : null)
                    .dni(solicitud.getInteresado1Dni())
                    .telefono(solicitud.getInteresado1Telefono())
                    .direccion(solicitud.getInteresado1Direccion())
                    .tipoVia(solicitud.getInteresado1TipoVia())
                    .nombreVia(solicitud.getInteresado1NombreVia())
                    .codigoPostal(solicitud.getInteresado1CodigoPostal())
                    .municipio(solicitud.getInteresado1Municipio())
                    .provincia(solicitud.getInteresado1Provincia())
                    .build());
        }
        if (hasInteresadoData(
                solicitud.getInteresado2Nombre(),
                solicitud.getInteresado2Dni(),
                solicitud.getInteresado2Telefono(),
                solicitud.getInteresado2Direccion(),
                solicitud.getInteresado2TipoVia(),
                solicitud.getInteresado2NombreVia(),
                solicitud.getInteresado2CodigoPostal(),
                solicitud.getInteresado2Municipio(),
                solicitud.getInteresado2Provincia(),
                solicitud.getInteresado2Rol() != null ? solicitud.getInteresado2Rol().name() : null)) {
            interesados.add(InteresadoSolicitudResponse.builder()
                    .nombre(solicitud.getInteresado2Nombre())
                    .rol(solicitud.getInteresado2Rol() != null ? solicitud.getInteresado2Rol().name() : null)
                    .dni(solicitud.getInteresado2Dni())
                    .telefono(solicitud.getInteresado2Telefono())
                    .direccion(solicitud.getInteresado2Direccion())
                    .tipoVia(solicitud.getInteresado2TipoVia())
                    .nombreVia(solicitud.getInteresado2NombreVia())
                    .codigoPostal(solicitud.getInteresado2CodigoPostal())
                    .municipio(solicitud.getInteresado2Municipio())
                    .provincia(solicitud.getInteresado2Provincia())
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

    private DateRange dateRange(String periodo, LocalDate fechaDesde, LocalDate fechaHasta) {
        LocalDate today = LocalDate.now();
        return switch (periodo != null ? periodo : "ESTE_MES") {
            case "ULTIMA_SEMANA" -> new DateRange(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "ULTIMOS_3_MESES" -> new DateRange(today.minusMonths(3).atStartOfDay(), null);
            case "ESTE_ANIO" -> new DateRange(today.withDayOfYear(1).atStartOfDay(), null);
            case "TODO" -> null;
            case "PERSONALIZADO" -> fechaDesde != null && fechaHasta != null && !fechaDesde.isAfter(fechaHasta)
                    ? new DateRange(fechaDesde.atStartOfDay(), fechaHasta.plusDays(1).atStartOfDay())
                    : new DateRange(today.plusYears(100).atStartOfDay(), today.plusYears(100).atStartOfDay());
            default -> new DateRange(today.withDayOfMonth(1).atStartOfDay(), null);
        };
    }

    private record DateRange(LocalDateTime desde, LocalDateTime hasta) {
    }
}
