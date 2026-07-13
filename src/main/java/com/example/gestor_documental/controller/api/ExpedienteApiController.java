package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.expediente.AccionMasivaExpedienteRequest;
import com.example.gestor_documental.dto.expediente.AccionMasivaExpedienteResponse;
import com.example.gestor_documental.dto.expediente.ActualizacionDocumentalExpedienteResponse;
import com.example.gestor_documental.dto.expediente.ActualizarInteresadosExpedienteRequest;
import com.example.gestor_documental.dto.expediente.ActualizarExpedienteRequest;
import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.CreacionConProcesamientoResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteEditCatalogsResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteListItemResponse;
import com.example.gestor_documental.dto.expediente.HitoAccionResponse;
import com.example.gestor_documental.dto.expediente.HitoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InteresadoExpedienteRequest;
import com.example.gestor_documental.dto.expediente.InteresadoSearchResponse;
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
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteCompletoProcesamientoService;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HitoExpedienteService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.TipoIncidenciaService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.impl.ExpedienteJustificanteFinalService;
import com.example.gestor_documental.service.impl.ExpedienteHaciendaDocumentacionService;
import com.example.gestor_documental.service.impl.ExpedienteDocumentacionActualizacionService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/expedientes")
@RequiredArgsConstructor
public class ExpedienteApiController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final ExpedienteService expedienteService;
    private final HitoExpedienteService hitoExpedienteService;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final InteresadoRepository interesadoRepository;
    private final MensajeService mensajeService;
    private final ClienteService clienteService;
    private final ExpedienteCompletoProcesamientoService expedienteCompletoProcesamientoService;
    private final TipoIncidenciaService tipoIncidenciaService;
    private final TipoTramiteService tipoTramiteService;
    private final CurrentUserService currentUserService;
    private final ExpedienteJustificanteFinalService justificanteFinalService;
    private final ExpedienteHaciendaDocumentacionService haciendaDocumentacionService;
    private final ExpedienteDocumentacionActualizacionService documentacionActualizacionService;

    @GetMapping
    public PagedResponse<ExpedienteListItemResponse> listarExpedientes(
            Authentication authentication,
            @RequestParam(required = false) EstadoExpediente estado,
            @RequestParam(required = false) List<EstadoExpediente> estados,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) String interesado,
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
        return PagedResponse.of(expedienteService.buscarListado(
                        clienteVisibleId,
                        estadosFiltro(estado, estados),
                        tipoTramiteId,
                        likeParam(matricula),
                        likeParam(interesado),
                        dateRange != null ? dateRange.desde() : null,
                        dateRange != null ? dateRange.hasta() : null,
                        pageRequest(pagina, tamanio)
                )
                .map(expediente -> mapExpedienteListItem(expediente, usuarioLogueado)));
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

    @GetMapping("/interesados/buscar")
    public List<InteresadoSearchResponse> buscarInteresados(
            @RequestParam(required = false) String q,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuario(authentication);
        String query = TextNormalizer.upperOrNull(q);
        if (query == null || query.length() < 2) {
            return List.of();
        }

        List<com.example.gestor_documental.model.Interesado> resultados;
        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            resultados = interesadoRepository
                    .findByDniContainingIgnoreCaseOrNombreContainingIgnoreCaseOrderByNombreAsc(
                            query,
                            query,
                            PageRequest.of(0, 8)
                    );
        } else if (usuarioLogueado.getCliente() != null) {
            java.util.List<com.example.gestor_documental.model.Interesado> resultadosCliente = new java.util.ArrayList<>(interesadoRepository.buscarPorClienteYTexto(
                    usuarioLogueado.getCliente().getId(),
                    query,
                    PageRequest.of(0, 8)
            ));
            clienteInteresadoRepository.findByClienteIdAndHabitualTrueOrderByInteresadoNombreAsc(usuarioLogueado.getCliente().getId()).stream()
                    .map(com.example.gestor_documental.model.ClienteInteresado::getInteresado)
                    .filter(interesado -> contiene(interesado.getDni(), query) || contiene(interesado.getNombre(), query))
                    .forEach(interesado -> {
                        if (resultadosCliente.stream().noneMatch(existente -> existente.getId().equals(interesado.getId()))) {
                            resultadosCliente.add(interesado);
                        }
                    });
            resultados = resultadosCliente.stream()
                    .sorted(Comparator.comparing(com.example.gestor_documental.model.Interesado::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .limit(8)
                    .toList();
        } else {
            resultados = List.of();
        }

        return resultados
                .stream()
                .map(interesado -> InteresadoSearchResponse.builder()
                        .id(interesado.getId())
                        .nombre(interesado.getNombre())
                        .nombrePila(interesado.getNombrePila())
                        .apellido1(interesado.getApellido1())
                        .apellido2(interesado.getApellido2())
                        .razonSocial(interesado.getRazonSocial())
                        .dni(interesado.getDni())
                        .telefono(interesado.getTelefono())
                        .direccion(interesado.getDireccion())
                        .tipoVia(interesado.getTipoVia())
                        .nombreVia(interesado.getNombreVia())
                        .numeroVia(interesado.getNumeroVia())
                        .bloque(interesado.getBloque())
                        .portal(interesado.getPortal())
                        .escalera(interesado.getEscalera())
                        .piso(interesado.getPiso())
                        .puerta(interesado.getPuerta())
                        .codigoPostal(interesado.getCodigoPostal())
                        .municipio(interesado.getMunicipio())
                        .provincia(interesado.getProvincia())
                        .tipoPersona(interesado.getTipoPersona() != null ? interesado.getTipoPersona().name() : null)
                        .build())
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> crearExpediente(
            @RequestBody ActualizarExpedienteRequest request,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        validarDatosBase(request);

        Expediente expediente = new Expediente();
        expediente.setMatricula(TextNormalizer.upperOrNull(request.getMatricula()));
        expediente.setObservaciones(TextNormalizer.upperOrNull(request.getObservaciones()));

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

    @PostMapping("/creacion-multiple")
    public CreacionConProcesamientoResponse crearExpedienteConProcesamiento(
            @RequestParam Long clienteId,
            @RequestParam Long tipoTramiteId,
            @RequestParam String matricula,
            @RequestParam(required = false) String observaciones,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = requireAdmin(authentication);
        if (matricula == null || matricula.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La matricula es obligatoria");
        }

        Expediente expediente = new Expediente();
        expediente.setMatricula(TextNormalizer.upperOrNull(matricula));
        expediente.setObservaciones(TextNormalizer.upperOrNull(observaciones) != null
                ? TextNormalizer.upperOrNull(observaciones)
                : "CREACION MULTIPLE");

        Expediente creado = expedienteService.crearExpedienteCompleto(
                expediente,
                usuarioLogueado,
                clienteId,
                tipoTramiteId,
                List.of(new InteresadoFormDto(), new InteresadoFormDto())
        );

        return new CreacionConProcesamientoResponse(
                creado.getId(),
                null,
                expedienteCompletoProcesamientoService.iniciar(creado.getId(), archivo, null, usuarioLogueado)
        );
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
        expedienteActualizado.setMatricula(TextNormalizer.upperOrNull(request.getMatricula()));
        expedienteActualizado.setObservaciones(TextNormalizer.upperOrNull(request.getObservaciones()));

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

    @PutMapping("/{id}/interesados")
    public ResponseEntity<Void> corregirInteresados(
            @PathVariable Long id,
            @RequestBody ActualizarInteresadosExpedienteRequest request,
            Authentication authentication
    ) {
        Usuario admin = requireAdmin(authentication);
        expedienteService.corregirInteresados(id, admin, mapInteresados(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/vinculo-tramite")
    public ResponseEntity<Void> vincularTramite(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        Usuario admin = requireAdmin(authentication);
        Long origenId = parseLong(body != null ? body.get("origenId") : null);
        if (origenId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el expediente origen");
        }
        expedienteService.vincularTramiteDependiente(id, origenId, body != null ? body.get("motivo") : null, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/vinculo-tramite/desvincular")
    public ResponseEntity<Void> desvincularTramite(
            @PathVariable Long id,
            Authentication authentication
    ) {
        expedienteService.desvincularTramiteDependiente(id, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documentacion/actualizar")
    public ActualizacionDocumentalExpedienteResponse actualizarDocumentacionExistente(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean forzarRelectura,
            Authentication authentication
    ) {
        return documentacionActualizacionService.actualizarDesdeDocumentos(id, requireAdmin(authentication), forzarRelectura);
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

    @PostMapping("/{id}/hitos/{codigo}/retroceder")
    public ResponseEntity<Void> retrocederHito(
            @PathVariable Long id,
            @PathVariable CodigoHitoExpediente codigo,
            Authentication authentication
    ) {
        hitoExpedienteService.retroceder(id, codigo, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalizar/retroceder")
    public ResponseEntity<Void> retrocederFinalizacion(@PathVariable Long id, Authentication authentication) {
        hitoExpedienteService.retrocederFinalizacion(id, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<Void> finalizar(@PathVariable Long id, Authentication authentication) {
        hitoExpedienteService.finalizar(id, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/acciones-masivas/avanzar")
    public AccionMasivaExpedienteResponse avanzarMasivo(
            @RequestBody AccionMasivaExpedienteRequest request,
            Authentication authentication
    ) {
        Usuario admin = requireAdmin(authentication);
        List<Long> ids = normalizarIds(request.getExpedienteIds());
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un expediente");
        }
        HitoAccionResponse accionObjetivo = accionPrincipal(expedienteDetalleApiService.obtenerDetalle(ids.get(0), admin).getSiguientePaso());
        if (accionObjetivo == null || accionObjetivo.getTipo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los expedientes seleccionados no tienen una accion masiva disponible");
        }
        if (request.getAccion() != null && !request.getAccion().equals(accionObjetivo.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La accion solicitada no coincide con el siguiente paso");
        }
        if (request.getCodigoHito() != null && accionObjetivo.getCodigoHito() != null
                && !request.getCodigoHito().equals(accionObjetivo.getCodigoHito())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El hito solicitado no coincide con el siguiente paso");
        }

        for (Long id : ids) {
            HitoAccionResponse accion = accionPrincipal(expedienteDetalleApiService.obtenerDetalle(id, admin).getSiguientePaso());
            if (!mismaAccion(accionObjetivo, accion)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Todos los expedientes deben estar en el mismo punto para avanzar en lote");
            }
        }

        for (Long id : ids) {
            ejecutarAccionMasiva(id, accionObjetivo, admin);
        }

        return AccionMasivaExpedienteResponse.builder()
                .total(ids.size())
                .mensaje("Se actualizaron " + ids.size() + " expedientes.")
                .build();
    }

    @GetMapping("/justificantes-finales")
    public void descargarJustificantesFinales(
            @RequestParam("ids") List<Long> expedienteIds,
            Authentication authentication,
            HttpServletResponse response
    ) throws IOException {
        Usuario admin = requireAdmin(authentication);
        List<Long> ids = normalizarIds(expedienteIds);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un expediente");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"justificantes_finales.zip\"");
        justificanteFinalService.escribirZipJustificantesFinales(ids, admin, response.getOutputStream());
    }

    @GetMapping("/documentacion-hacienda")
    public void descargarDocumentacionHacienda(
            @RequestParam("ids") List<Long> expedienteIds,
            Authentication authentication,
            HttpServletResponse response
    ) throws IOException {
        Usuario admin = requireAdmin(authentication);
        List<Long> ids = normalizarIds(expedienteIds);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un expediente");
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"documentacion_hacienda_620.zip\"");
        haciendaDocumentacionService.escribirZipDocumentacionHacienda(ids, admin, response.getOutputStream());
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

    @PostMapping("/{id}/mensajes/leidos")
    public ResponseEntity<Void> marcarMensajesLeidos(@PathVariable Long id, Authentication authentication) {
        mensajeService.marcarLeidosExpediente(id, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/informacion-adicional")
    public ResponseEntity<Void> solicitarInformacionAdicional(
            @PathVariable Long id,
            @RequestParam(required = false) String contenido,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication authentication
    ) {
        Usuario admin = requireAdmin(authentication);
        String contenidoFinal = contenido != null ? contenido : body != null ? body.get("contenido") : null;
        expedienteService.solicitarInformacionAdicional(id, admin);
        mensajeService.a\u00f1adirAExpediente(id, contenidoFinal, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/informacion-adicional/revisar")
    public ResponseEntity<Void> revisarInformacionAdicional(
            @PathVariable Long id,
            Authentication authentication
    ) {
        expedienteService.resolverInformacionAdicional(id, requireAdmin(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tipos-incidencia")
    public java.util.List<TipoIncidenciaResponse> listarTiposIncidencia() {
        return tipoIncidenciaService.listarTodosActivos().stream()
                .filter(tipo -> tipo.getNombre() != com.example.gestor_documental.enums.TipoIncidenciaEnum.SOLICITADA_INFORMACION_ADICIONAL)
                .map(tipo -> TipoIncidenciaResponse.builder()
                        .id(tipo.getId())
                        .nombre(tipo.getNombre() != null ? tipo.getNombre().name() : null)
                        .descripcion(tipo.getDescripcion())
                        .build())
                .toList();
    }

    private Usuario usuario(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }

    private Usuario requireAdmin(Authentication authentication) {
        return currentUserService.requireAdmin(authentication);
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
        return mapInteresado(request.getInteresados().get(index));
    }

    private List<InteresadoFormDto> mapInteresados(ActualizarExpedienteRequest request) {
        if (request.getInteresados() == null || request.getInteresados().isEmpty()) {
            return List.of(mapInteresado(request, 0), mapInteresado(request, 1));
        }
        return request.getInteresados().stream().map(this::mapInteresado).toList();
    }

    private List<InteresadoFormDto> mapInteresados(ActualizarInteresadosExpedienteRequest request) {
        if (request.getInteresados() == null) {
            return List.of();
        }
        return request.getInteresados().stream().map(this::mapInteresado).toList();
    }

    private InteresadoFormDto mapInteresado(InteresadoExpedienteRequest interesado) {
        InteresadoFormDto dto = new InteresadoFormDto();
        dto.setNombre(TextNormalizer.upperOrNull(interesado.getNombre()));
        dto.setNombrePila(TextNormalizer.upperOrNull(interesado.getNombrePila()));
        dto.setApellido1(TextNormalizer.upperOrNull(interesado.getApellido1()));
        dto.setApellido2(TextNormalizer.upperOrNull(interesado.getApellido2()));
        dto.setRazonSocial(TextNormalizer.upperOrNull(interesado.getRazonSocial()));
        dto.setDni(TextNormalizer.upperOrNull(interesado.getDni()));
        dto.setTelefono(TextNormalizer.upperOrNull(interesado.getTelefono()));
        dto.setDireccion(TextNormalizer.upperOrNull(interesado.getDireccion()));
        dto.setTipoVia(TextNormalizer.upperOrNull(interesado.getTipoVia()));
        dto.setNombreVia(TextNormalizer.upperOrNull(interesado.getNombreVia()));
        dto.setNumeroVia(TextNormalizer.upperOrNull(interesado.getNumeroVia()));
        dto.setBloque(TextNormalizer.upperOrNull(interesado.getBloque()));
        dto.setPortal(TextNormalizer.upperOrNull(interesado.getPortal()));
        dto.setEscalera(TextNormalizer.upperOrNull(interesado.getEscalera()));
        dto.setPiso(TextNormalizer.upperOrNull(interesado.getPiso()));
        dto.setPuerta(TextNormalizer.upperOrNull(interesado.getPuerta()));
        dto.setCodigoPostal(TextNormalizer.upperOrNull(interesado.getCodigoPostal()));
        dto.setMunicipio(TextNormalizer.upperOrNull(interesado.getMunicipio()));
        dto.setProvincia(TextNormalizer.upperOrNull(interesado.getProvincia()));
        dto.setRol(interesado.getRol());
        return dto;
    }

    private ExpedienteListItemResponse mapExpedienteListItem(Expediente expediente, Usuario usuario) {
        ExpedienteDetailResponse detalle = expedienteDetalleApiService.obtenerDetalle(expediente.getId(), usuario);
        HitoAccionResponse siguienteAccion = accionPrincipal(detalle.getSiguientePaso());
        return ExpedienteListItemResponse.builder()
                .id(expediente.getId())
                .matricula(expediente.getMatricula())
                .tipoTramite(expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                        ? expediente.getTipoTramite().getNombre().name()
                        : null)
                .estado(expediente.getEstadoExpediente() != null ? expediente.getEstadoExpediente().name() : null)
                .incidenciasActivas(detalle.getIncidencias().stream()
                        .filter(incidencia -> !incidencia.isResuelta())
                        .map(incidencia -> incidencia.getTipo())
                        .filter(tipo -> tipo != null && !tipo.isBlank())
                        .distinct()
                        .toList())
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
                .interesados(detalle.getInteresados())
                .faseActual(detalle.getFaseActual())
                .siguientePasoTitulo(detalle.getSiguientePaso() != null ? detalle.getSiguientePaso().getTitulo() : null)
                .siguientePasoDetalle(detalle.getSiguientePaso() != null
                        ? primerTextoDisponible(detalle.getSiguientePaso().getNota(), detalle.getSiguientePaso().getDescripcion())
                        : null)
                .siguienteAccion(siguienteAccion)
                .justificantesFinalesDisponibles(justificanteFinalService.tieneJustificantesFinales(expediente.getId(), detalle.getEstado()))
                .justificantesFinalesPendientes(justificanteFinalService.justificantesFinalesPendientes(expediente.getId(), detalle.getEstado()))
                .documentacionHaciendaDisponible(haciendaDocumentacionService.tieneDocumentacionHaciendaDisponible(detalle))
                .build();
    }

    private String primerTextoDisponible(String principal, String alternativa) {
        return principal != null && !principal.isBlank() ? principal : alternativa;
    }

    private HitoAccionResponse accionPrincipal(HitoExpedienteResponse siguientePaso) {
        if (siguientePaso == null || siguientePaso.isCompletado() || siguientePaso.isBloqueado()) {
            return null;
        }
        if (siguientePaso.getAcciones() != null && !siguientePaso.getAcciones().isEmpty()) {
            return siguientePaso.getAcciones().stream()
                    .filter(this::esAccionMasivaSoportada)
                    .findFirst()
                    .orElse(null);
        }
        if (siguientePaso.getAccion() == null) {
            return null;
        }
        HitoAccionResponse accion = HitoAccionResponse.builder()
                .tipo(siguientePaso.getAccion())
                .label(siguientePaso.getAccionLabel())
                .codigoHito(codigoHitoDesdeId(siguientePaso.getId()))
                .tono("primary")
                .build();
        return esAccionMasivaSoportada(accion) ? accion : null;
    }

    private boolean esAccionMasivaSoportada(HitoAccionResponse accion) {
        return accion != null
                && ("FINALIZAR".equals(accion.getTipo())
                || ("COMPLETAR_HITO".equals(accion.getTipo()) && accion.getCodigoHito() != null));
    }

    private String codigoHitoDesdeId(String id) {
        if ("tramite-programa-gestion".equals(id)) {
            return CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION.name();
        }
        if ("modelo-620-presentado".equals(id)) {
            return CodigoHitoExpediente.MODELO_620_PRESENTADO.name();
        }
        return null;
    }

    private boolean mismaAccion(HitoAccionResponse expected, HitoAccionResponse actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return java.util.Objects.equals(expected.getTipo(), actual.getTipo())
                && java.util.Objects.equals(expected.getCodigoHito(), actual.getCodigoHito());
    }

    private void ejecutarAccionMasiva(Long expedienteId, HitoAccionResponse accion, Usuario admin) {
        if ("FINALIZAR".equals(accion.getTipo())) {
            hitoExpedienteService.finalizar(expedienteId, admin);
            return;
        }
        if ("COMPLETAR_HITO".equals(accion.getTipo()) && accion.getCodigoHito() != null) {
            hitoExpedienteService.completar(expedienteId, CodigoHitoExpediente.valueOf(accion.getCodigoHito()), admin);
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La accion masiva no esta soportada");
    }

    private List<Long> normalizarIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new java.util.ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(java.util.Objects::nonNull)
                .toList()));
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

    private List<EstadoExpediente> estadosFiltro(EstadoExpediente estado, List<EstadoExpediente> estados) {
        if (estados != null && !estados.isEmpty()) {
            return estados.stream().distinct().toList();
        }
        return estado != null ? List.of(estado) : List.of();
    }

    private Long parseLong(String valor) {
        try {
            return valor != null && !valor.isBlank() ? Long.parseLong(valor.trim()) : null;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de expediente no valido");
        }
    }

    private boolean contiene(String valor, String query) {
        String normalizado = TextNormalizer.upperOrNull(valor);
        return normalizado != null && normalizado.contains(query);
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
