package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;

import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.CreacionConProcesamientoResponse;
import com.example.gestor_documental.dto.expediente.DocumentoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.dto.expediente.HistorialExpedienteResponse;
import com.example.gestor_documental.dto.expediente.IncidenciaExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InteresadoSolicitudResponse;
import com.example.gestor_documental.dto.expediente.ListCatalogsResponse;
import com.example.gestor_documental.dto.expediente.MensajeExpedienteResponse;
import com.example.gestor_documental.dto.expediente.ProcesamientoExpedienteCompletoResponse;
import com.example.gestor_documental.dto.expediente.SolicitudUpsertRequest;
import com.example.gestor_documental.dto.expediente.SolicitudBulkConvertRequest;
import com.example.gestor_documental.dto.expediente.SolicitudBulkConvertResponse;
import com.example.gestor_documental.dto.expediente.SolicitudBulkConvertResponse.SolicitudBulkConvertResult;
import com.example.gestor_documental.dto.expediente.SolicitudDetailResponse;
import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.dto.expediente.SolicitudIdentidadDetectadaRequest;
import com.example.gestor_documental.dto.expediente.SolicitudInteresadoCoincidenciaResponse;
import com.example.gestor_documental.dto.expediente.SolicitudListItemResponse;
import com.example.gestor_documental.dto.expediente.SolicitudPreparacionTraspasoResponse;
import com.example.gestor_documental.dto.expediente.TipoTramiteResumenResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.dto.expediente.SolicitudVehiculoResponse;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.model.ClienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteCompletoProcesamientoService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
import com.example.gestor_documental.service.SolicitudPreparacionTraspasoService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.impl.CorreoEntranteSolicitudService;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.util.DocumentoIdentidadLecturaJson;
import com.example.gestor_documental.util.TextNormalizer;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private static final double CONFIANZA_MINIMA_IDENTIDAD_SOLICITUD = 0.80;

    private final SolicitudService solicitudService;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;
    private final DocumentoService documentoService;
    private final ExpedienteCompletoProcesamientoService expedienteCompletoProcesamientoService;
    private final IncidenciaService incidenciaService;
    private final HistorialCambioService historialCambioService;
    private final MensajeService mensajeService;
    private final CurrentUserService currentUserService;
    private final CorreoEntranteSolicitudService correoEntranteSolicitudService;
    private final SolicitudDocumentacionIaService solicitudDocumentacionIaService;
    private final SolicitudPreparacionTraspasoService solicitudPreparacionTraspasoService;
    private final DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    private final DocumentoRolesLecturaRepository documentoRolesLecturaRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;

    @GetMapping
    public PagedResponse<SolicitudListItemResponse> listarSolicitudes(
            Authentication authentication,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false, defaultValue = "ACTIVAS") String archivo,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false, defaultValue = "ULTIMA_SEMANA") String periodo
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
                        archivo,
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

        return mapSolicitudDetail(solicitud, usuarioLogueado);
    }

    @GetMapping("/{id}/preparacion-traspaso")
    public SolicitudPreparacionTraspasoResponse obtenerPreparacionTraspaso(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        return solicitudPreparacionTraspasoService.obtenerPreparacion(id, usuarioLogueado);
    }

    @PostMapping("/{id}/convertir")
    public ResponseEntity<java.util.Map<String, Long>> convertir(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        Expediente expediente = solicitudService.convertirAExpediente(id, usuarioLogueado);
        return ResponseEntity.ok(java.util.Map.of("id", expediente.getId()));
    }

    @PostMapping("/{id}/documentacion-ia/procesar")
    public SolicitudDocumentacionIaResponse procesarDocumentacionIa(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") boolean forzarRelectura,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        return solicitudDocumentacionIaService.procesarDocumentacion(id, usuarioLogueado, forzarRelectura);
    }

    @PostMapping("/{id}/documentacion-ia/cliente")
    public SolicitudDocumentacionIaResponse procesarDocumentacionIaCliente(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        return solicitudDocumentacionIaService.procesarDocumentacionCliente(id, usuarioLogueado);
    }

    @PostMapping("/{id}/documentacion-ia/reset")
    public SolicitudDetailResponse resetDatosLecturaIa(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        Solicitud solicitud = solicitudService.resetDatosLecturaIa(id, usuarioLogueado);
        return mapSolicitudDetail(solicitud, usuarioLogueado);
    }

    @GetMapping("/{id}/interesados/coincidencias")
    public List<SolicitudInteresadoCoincidenciaResponse> revisarCoincidenciasInteresados(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        return solicitudService.buscarCoincidenciasInteresadosConDiferencias(id, usuarioLogueado);
    }

    @PostMapping("/{id}/interesados/detectados")
    public SolicitudDetailResponse anadirInteresadoDetectado(
            @PathVariable Long id,
            @RequestBody SolicitudIdentidadDetectadaRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        Solicitud solicitud = solicitudService.anadirInteresadoDetectado(id, request, usuarioLogueado);
        return mapSolicitudDetail(solicitud, usuarioLogueado);
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

    @PostMapping("/creacion-multiple")
    public CreacionConProcesamientoResponse crearSolicitudConProcesamiento(
            @RequestParam Long tipoTramiteId,
            @RequestParam String matricula,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        if (usuarioLogueado.getCliente() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes un cliente asociado");
        }
        if (matricula == null || matricula.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La matricula es obligatoria");
        }

        Solicitud solicitud = new Solicitud();
        solicitud.setMatricula(TextNormalizer.upperOrNull(matricula));
        solicitud.setObservaciones("CREACION MULTIPLE");

        Solicitud creada = solicitudService.crearSolicitudCompleta(
                solicitud,
                usuarioLogueado,
                usuarioLogueado.getCliente(),
                tipoTramiteId
        );

        return new CreacionConProcesamientoResponse(
                null,
                creada.getId(),
                expedienteCompletoProcesamientoService.iniciarSolicitud(creada.getId(), archivo, usuarioLogueado)
        );
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        solicitudService.eliminarSolicitudErronea(id, usuarioLogueado);
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

    @PostMapping("/{id}/documentos/expediente-completo/procesamientos")
    public ProcesamientoExpedienteCompletoResponse iniciarProcesamientoExpedienteCompleto(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        return expedienteCompletoProcesamientoService.iniciarSolicitud(id, archivo, usuarioLogueado);
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
        solicitud.setVehiculoMarca(TextNormalizer.upperOrNull(request.getVehiculoMarca()));
        solicitud.setVehiculoModelo(TextNormalizer.upperOrNull(request.getVehiculoModelo()));
        solicitud.setVehiculoBastidor(TextNormalizer.upperOrNull(request.getVehiculoBastidor()));
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
        solicitud.setInteresado3Rol(request.getInteresado3Rol());
        solicitud.setInteresado3Nombre(TextNormalizer.upperOrNull(request.getInteresado3Nombre()));
        solicitud.setInteresado3Dni(TextNormalizer.upperOrNull(request.getInteresado3Dni()));
        solicitud.setInteresado3Telefono(TextNormalizer.upperOrNull(request.getInteresado3Telefono()));
        solicitud.setInteresado3Direccion(TextNormalizer.upperOrNull(request.getInteresado3Direccion()));
        solicitud.setInteresado3TipoVia(TextNormalizer.upperOrNull(request.getInteresado3TipoVia()));
        solicitud.setInteresado3NombreVia(TextNormalizer.upperOrNull(request.getInteresado3NombreVia()));
        solicitud.setInteresado3CodigoPostal(TextNormalizer.upperOrNull(request.getInteresado3CodigoPostal()));
        solicitud.setInteresado3Municipio(TextNormalizer.upperOrNull(request.getInteresado3Municipio()));
        solicitud.setInteresado3Provincia(TextNormalizer.upperOrNull(request.getInteresado3Provincia()));
        return solicitud;
    }

    private SolicitudListItemResponse mapSolicitudListItem(Solicitud solicitud) {
        List<Documento> documentos = documentoService.listarPorSolicitud(solicitud.getId());
        List<InteresadoSolicitudResponse> interesados = mapInteresados(solicitud, documentos, true);
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
                .interesados(interesados)
                .situacionDocumental(situacionDocumental(solicitud, documentos, interesados))
                .build();
    }

    private String situacionDocumental(Solicitud solicitud, List<Documento> documentos, List<InteresadoSolicitudResponse> interesados) {
        if (solicitudArchivada(solicitud)) {
            return "ARCHIVADA";
        }
        if (documentos.isEmpty()) return "SIN DOCUMENTACION";
        int pendientes = contarDocumentosBasicosPendientes(solicitud, documentos, interesados);
        if (pendientes > 0) return pendientes == 1 ? "FALTA 1 DOCUMENTO" : "FALTAN " + pendientes + " DOCUMENTOS";
        return "DOCUMENTACION COMPLETA";
    }

    private boolean solicitudArchivada(Solicitud solicitud) {
        return solicitud.getExpediente() != null
                || solicitud.getEstadoSolicitud() == EstadoSolicitud.CONVERTIDA
                || solicitud.getEstadoSolicitud() == EstadoSolicitud.RECHAZADO;
    }

    private int contarDocumentosBasicosPendientes(Solicitud solicitud, List<Documento> documentos, List<InteresadoSolicitudResponse> interesados) {
        Set<TipoDocumento> tipos = documentos.stream().map(Documento::getTipoDocumento).collect(Collectors.toSet());
        int pendientes = 0;
        TipoTramiteEnum tramite = solicitud.getTipoTramite() != null ? solicitud.getTipoTramite().getNombre() : null;
        int identidadesEsperadas = identidadesEsperadasSolicitud(tramite);
        long identidadesAportadas = documentos.stream()
                .filter(documento -> documento.getTipoDocumento() == TipoDocumento.DNI
                        || documento.getTipoDocumento() == TipoDocumento.CIF)
                .count();
        long identidadesCubiertas = interesados.stream()
                .filter(InteresadoSolicitudResponse::isDocumentoIdentidadAportado)
                .count();
        int identidadesValidas = Math.max(
                Math.min(identidadesEsperadas, (int) identidadesAportadas),
                Math.min(identidadesEsperadas, (int) identidadesCubiertas)
        );
        pendientes += Math.max(0, identidadesEsperadas - identidadesValidas);
        pendientes += (int) interesados.stream()
                .filter(interesado -> interesado.isRequiereRepresentanteLegal() && !interesado.isRepresentanteLegalAportado())
                .count();

        boolean requiereContrato = tramite == TipoTramiteEnum.TRASPASO
                || tramite == TipoTramiteEnum.BATECOM
                || tramite == TipoTramiteEnum.NOTIFICACION_VENTA;
        if (tramite == TipoTramiteEnum.BATECOM) {
            long documentosRol = documentos.stream()
                    .filter(documento -> documento.getTipoDocumento() == TipoDocumento.CONTRATO_COMPRAVENTA
                            || documento.getTipoDocumento() == TipoDocumento.FACTURA)
                    .count();
            pendientes += Math.max(0, 2 - (int) documentosRol);
        } else if (requiereContrato && !tipos.contains(TipoDocumento.CONTRATO_COMPRAVENTA) && !tipos.contains(TipoDocumento.FACTURA)) {
            pendientes++;
        }
        if (!tipos.contains(TipoDocumento.MANDATO) && !tipos.contains(TipoDocumento.MANDATO_REPRESENTACION)) pendientes++;
        boolean informeDgt = tipos.contains(TipoDocumento.INFORME_DGT);
        if (!informeDgt && !tipos.contains(TipoDocumento.PERMISO_CIRCULACION)) pendientes++;
        if (!informeDgt && !tipos.contains(TipoDocumento.FICHA_TECNICA)) pendientes++;
        return pendientes;
    }

    private int identidadesEsperadasSolicitud(TipoTramiteEnum tramite) {
        if (tramite == TipoTramiteEnum.BATECOM) {
            return 3;
        }
        if (tramite == TipoTramiteEnum.TRASPASO || tramite == TipoTramiteEnum.NOTIFICACION_VENTA) {
            return 2;
        }
        return 1;
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

    private SolicitudDetailResponse mapSolicitudDetail(Solicitud solicitud, Usuario usuarioLogueado) {
        List<Documento> documentos = documentoService.listarPorSolicitud(solicitud.getId());
        List<InteresadoSolicitudResponse> interesados = mapInteresados(solicitud, documentos, true);
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
                .situacionDocumental(situacionDocumental(solicitud, documentos, interesados))
                .expedienteId(solicitud.getExpediente() != null ? solicitud.getExpediente().getId() : null)
                .vehiculo(new SolicitudVehiculoResponse(
                        solicitud.getMatricula(),
                        solicitud.getVehiculoMarca(),
                        solicitud.getVehiculoModelo(),
                        solicitud.getVehiculoBastidor()))
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
                .lecturaIaCliente(usuarioLogueado.getRolUsuario() == RolUsuario.CLIENTE
                        ? solicitudDocumentacionIaService.obtenerLecturaCliente(solicitud.getId(), usuarioLogueado)
                        : null)
                .interesados(interesados)
                .documentos(documentos.stream().map(this::mapDocumento).toList())
                .incidencias(incidenciaService.listarPorSolicitud(solicitud.getId()).stream().map(this::mapIncidencia).toList())
                .historial(historialCambioService.listarPorSolicitud(solicitud.getId()).stream()
                        .filter(cambio -> !"CARGAR DOCUMENTO".equals(cambio.getAccion()))
                        .map(this::mapHistorial)
                        .toList())
                .mensajes(mensajeService.listarPorSolicitud(solicitud.getId()).stream().map(this::mapMensaje).toList())
                .build();
    }

    private List<InteresadoSolicitudResponse> mapInteresados(Solicitud solicitud, List<Documento> documentos, boolean incluirSoporteCliente) {
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
            interesados.add(mapInteresadoSolicitud(
                    solicitud,
                    documentos,
                    solicitud.getInteresado1Nombre(),
                    solicitud.getInteresado1Rol() != null ? solicitud.getInteresado1Rol().name() : null,
                    solicitud.getInteresado1Dni(),
                    solicitud.getInteresado1Telefono(),
                    solicitud.getInteresado1Direccion(),
                    solicitud.getInteresado1TipoVia(),
                    solicitud.getInteresado1NombreVia(),
                    solicitud.getInteresado1CodigoPostal(),
                    solicitud.getInteresado1Municipio(),
                    solicitud.getInteresado1Provincia(),
                    incluirSoporteCliente));
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
            interesados.add(mapInteresadoSolicitud(
                    solicitud,
                    documentos,
                    solicitud.getInteresado2Nombre(),
                    solicitud.getInteresado2Rol() != null ? solicitud.getInteresado2Rol().name() : null,
                    solicitud.getInteresado2Dni(),
                    solicitud.getInteresado2Telefono(),
                    solicitud.getInteresado2Direccion(),
                    solicitud.getInteresado2TipoVia(),
                    solicitud.getInteresado2NombreVia(),
                    solicitud.getInteresado2CodigoPostal(),
                    solicitud.getInteresado2Municipio(),
                    solicitud.getInteresado2Provincia(),
                    incluirSoporteCliente));
        }
        if (hasInteresadoData(
                solicitud.getInteresado3Nombre(),
                solicitud.getInteresado3Dni(),
                solicitud.getInteresado3Telefono(),
                solicitud.getInteresado3Direccion(),
                solicitud.getInteresado3TipoVia(),
                solicitud.getInteresado3NombreVia(),
                solicitud.getInteresado3CodigoPostal(),
                solicitud.getInteresado3Municipio(),
                solicitud.getInteresado3Provincia(),
                solicitud.getInteresado3Rol() != null ? solicitud.getInteresado3Rol().name() : null)) {
            interesados.add(mapInteresadoSolicitud(
                    solicitud,
                    documentos,
                    solicitud.getInteresado3Nombre(),
                    solicitud.getInteresado3Rol() != null ? solicitud.getInteresado3Rol().name() : null,
                    solicitud.getInteresado3Dni(),
                    solicitud.getInteresado3Telefono(),
                    solicitud.getInteresado3Direccion(),
                    solicitud.getInteresado3TipoVia(),
                    solicitud.getInteresado3NombreVia(),
                    solicitud.getInteresado3CodigoPostal(),
                    solicitud.getInteresado3Municipio(),
                    solicitud.getInteresado3Provincia(),
                    incluirSoporteCliente));
        }
        if (solicitud.getTipoTramite() != null && solicitud.getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM) {
            interesados.sort(java.util.Comparator.comparingInt(item -> ordenRolBatecom(item.getRol())));
        }
        return interesados;
    }

    private int ordenRolBatecom(String rol) {
        if ("VENDEDOR".equals(rol)) return 0;
        if ("COMPRAVENTA".equals(rol)) return 1;
        if ("COMPRADOR".equals(rol)) return 2;
        return 3;
    }

    private boolean hasInteresadoData(String... values) {
        return java.util.Arrays.stream(values).anyMatch(value -> value != null && !value.trim().isEmpty());
    }

    private InteresadoSolicitudResponse mapInteresadoSolicitud(
            Solicitud solicitud,
            List<Documento> documentos,
            String nombre,
            String rol,
            String dni,
            String telefono,
            String direccion,
            String tipoVia,
            String nombreVia,
            String codigoPostal,
            String municipio,
            String provincia,
            boolean incluirSoporteCliente
    ) {
        boolean personaJuridica = esPersonaJuridica(dni);
        DocumentoSoporte identidad = soporteIdentidad(solicitud, documentos, dni, personaJuridica, incluirSoporteCliente);
        RepresentanteSoporte representante = personaJuridica && incluirSoporteCliente
                ? soporteRepresentanteLegal(solicitud, dni)
                : RepresentanteSoporte.noAplica();

        return InteresadoSolicitudResponse.builder()
                .nombre(nombre)
                .rol(rol)
                .dni(dni)
                .telefono(telefono)
                .direccion(direccion)
                .tipoVia(tipoVia)
                .nombreVia(nombreVia)
                .codigoPostal(codigoPostal)
                .municipio(municipio)
                .provincia(provincia)
                .personaJuridica(personaJuridica)
                .documentoIdentidadAportado(identidad.aportado())
                .documentoIdentidadOrigen(identidad.origen())
                .requiereRepresentanteLegal(personaJuridica)
                .representanteLegalAportado(representante.aportado())
                .representanteLegalNombre(representante.nombre())
                .representanteLegalDni(representante.dni())
                .build();
    }

    private DocumentoSoporte soporteIdentidad(
            Solicitud solicitud,
            List<Documento> documentos,
            String dni,
            boolean personaJuridica,
            boolean incluirSoporteCliente
    ) {
        String identificador = normalizarIdentificador(dni);
        if (identificador.isBlank()) {
            return DocumentoSoporte.noAportado();
        }
        if (documentos.stream().anyMatch(documento -> documentoLecturaCoincide(documento, identificador))) {
            return new DocumentoSoporte(true, "SOLICITUD");
        }
        if (!incluirSoporteCliente) {
            return DocumentoSoporte.noAportado();
        }
        if (solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
            return DocumentoSoporte.noAportado();
        }
        String nifCliente = normalizarIdentificador(solicitud.getCliente().getNif());
        if (!identificador.equals(nifCliente)) {
            return DocumentoSoporte.noAportado();
        }
        TipoDocumento tipoEsperado = personaJuridica ? TipoDocumento.CIF : TipoDocumento.DNI;
        boolean existeEnFichaCliente = documentoService.listarPorCliente(solicitud.getCliente().getId()).stream()
                .anyMatch(documento -> documento.getTipoDocumento() == tipoEsperado
                        && (personaJuridica || documentoLecturaCoincide(documento, identificador)));
        return existeEnFichaCliente ? new DocumentoSoporte(true, "FICHA_CLIENTE") : DocumentoSoporte.noAportado();
    }

    private RepresentanteSoporte soporteRepresentanteLegal(Solicitud solicitud, String dniEmpresa) {
        if (solicitud.getCliente() == null || solicitud.getCliente().getId() == null) {
            return RepresentanteSoporte.noAportado();
        }
        String nifCliente = normalizarIdentificador(solicitud.getCliente().getNif());
        String nifEmpresa = normalizarIdentificador(dniEmpresa);
        if (nifCliente.isBlank() || !nifCliente.equals(nifEmpresa)) {
            return RepresentanteSoporte.noAportado();
        }
        List<ClienteInteresado> representantes = clienteInteresadoRepository
                .findByClienteIdAndRepresentanteLegalTrueOrderByInteresadoNombreAsc(solicitud.getCliente().getId());
        RepresentanteSoporte registradoSinDocumento = RepresentanteSoporte.noAportado();
        for (ClienteInteresado relacion : representantes) {
            Interesado representante = relacion.getInteresado();
            if (representante == null || representante.getId() == null) {
                continue;
            }
            String nombre = representante.getNombre();
            String dni = representante.getDni();
            if (registradoSinDocumento.nombre() == null) {
                registradoSinDocumento = new RepresentanteSoporte(false, nombre, dni);
            }
            boolean tieneDni = documentoService
                    .listarPorInteresadoHabitual(solicitud.getCliente().getId(), representante.getId()).stream()
                    .anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.DNI);
            if (tieneDni) {
                return new RepresentanteSoporte(true, nombre, dni);
            }
        }
        return registradoSinDocumento;
    }

    private boolean documentoLecturaCoincide(Documento documento, String identificador) {
        if (documento == null || identificador == null || identificador.isBlank()) {
            return false;
        }
        if (documento.getId() == null) {
            return false;
        }
        DocumentoIdentidadLectura lectura = documentoIdentidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null);
        if (lectura == null || lectura.getConfianzaGlobal() == null || lectura.getConfianzaGlobal() < CONFIANZA_MINIMA_IDENTIDAD_SOLICITUD) {
            return false;
        }
        if (identificador.equals(normalizarIdentificador(lectura.getIdentificador()))) {
            return true;
        }
        return DocumentoIdentidadLecturaJson.extraer(lectura).stream()
                .filter(item -> item.confianzaGlobal() != null && item.confianzaGlobal() >= CONFIANZA_MINIMA_IDENTIDAD_SOLICITUD)
                .anyMatch(item -> identificador.equals(normalizarIdentificador(item.identificador())));
    }

    private boolean esPersonaJuridica(String identificador) {
        return normalizarIdentificador(identificador).matches("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");
    }

    private String normalizarIdentificador(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private record DocumentoSoporte(boolean aportado, String origen) {
        private static DocumentoSoporte noAportado() {
            return new DocumentoSoporte(false, null);
        }
    }

    private record RepresentanteSoporte(boolean aportado, String nombre, String dni) {
        private static RepresentanteSoporte noAportado() {
            return new RepresentanteSoporte(false, null, null);
        }

        private static RepresentanteSoporte noAplica() {
            return new RepresentanteSoporte(false, null, null);
        }
    }

    private DocumentoExpedienteResponse mapDocumento(Documento documento) {
        DocumentoIdentidadLectura lecturaIdentidad = documento.getId() != null
                ? documentoIdentidadLecturaRepository.findByDocumentoId(documento.getId()).orElse(null)
                : null;
        DocumentoRolesLectura lecturaRoles = documento.getId() != null
                ? documentoRolesLecturaRepository.findByDocumentoId(documento.getId()).orElse(null)
                : null;
        return DocumentoExpedienteResponse.builder()
                .id(documento.getId())
                .nombre(documento.getNombreArchivo())
                .nombreOriginal(documento.getNombreArchivoOriginal())
                .tipo(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : null)
                .descripcion(documento.getDescripcionArchivo())
                .fechaSubida(formatDate(documento.getFechaSubida()))
                .subidoPor(documento.getSubidoPor() != null ? nombreCompleto(documento.getSubidoPor()) : null)
                .interesadoId(documento.getInteresado() != null ? documento.getInteresado().getId() : null)
                .interesadoNombre(documento.getInteresado() != null ? documento.getInteresado().getNombre() : null)
                .estado("SUBIDO")
                .subido(true)
                .requeridoAhora(false)
                .lecturaIdentidad(DocumentoIdentidadLecturaResponse.from(lecturaIdentidad))
                .lecturaRoles(DocumentoRolesLecturaResponse.from(lecturaRoles))
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
        return switch (periodo != null ? periodo : "ULTIMA_SEMANA") {
            case "ULTIMA_SEMANA" -> new DateRange(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "MES_ANTERIOR" -> new DateRange(today.minusMonths(1).withDayOfMonth(1).atStartOfDay(), today.withDayOfMonth(1).atStartOfDay());
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
