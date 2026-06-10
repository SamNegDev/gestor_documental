package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.InteresadoFormDto;
import com.example.gestor_documental.dto.expediente.AccionMasivaExpedienteRequest;
import com.example.gestor_documental.dto.expediente.AccionMasivaExpedienteResponse;
import com.example.gestor_documental.dto.expediente.ActualizarExpedienteRequest;
import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteEditCatalogsResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteListItemResponse;
import com.example.gestor_documental.dto.expediente.HitoAccionResponse;
import com.example.gestor_documental.dto.expediente.HitoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InteresadoSearchResponse;
import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.dto.expediente.ListCatalogsResponse;
import com.example.gestor_documental.dto.expediente.TipoIncidenciaResponse;
import com.example.gestor_documental.dto.expediente.TipoTramiteResumenResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HitoExpedienteService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.TipoIncidenciaService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/expedientes")
@RequiredArgsConstructor
public class ExpedienteApiController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExpedienteDetalleApiService expedienteDetalleApiService;
    private final ExpedienteService expedienteService;
    private final HitoExpedienteService hitoExpedienteService;
    private final DocumentoRepository documentoRepository;
    private final InteresadoRepository interesadoRepository;
    private final MensajeService mensajeService;
    private final ClienteService clienteService;
    private final TipoIncidenciaService tipoIncidenciaService;
    private final TipoTramiteService tipoTramiteService;
    private final UsuarioService usuarioService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

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
                    .filter(expediente -> isWithinRange(fechaReferencia(expediente.getFechaCreacion(), expediente.getFechaUltimaModificacion()), dateRange))
                    .toList();
        }

        return expedientes.stream().map(expediente -> mapExpedienteListItem(expediente, usuarioLogueado)).toList();
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
            resultados = interesadoRepository.buscarPorClienteYTexto(
                    usuarioLogueado.getCliente().getId(),
                    query,
                    PageRequest.of(0, 8)
            );
        } else {
            resultados = List.of();
        }

        return resultados
                .stream()
                .map(interesado -> InteresadoSearchResponse.builder()
                        .id(interesado.getId())
                        .nombre(interesado.getNombre())
                        .dni(interesado.getDni())
                        .telefono(interesado.getTelefono())
                        .direccion(interesado.getDireccion())
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

        Map<String, List<Documento>> documentosPorCarpeta = new java.util.LinkedHashMap<>();
        for (Long id : ids) {
            ExpedienteDetailResponse detalle = expedienteDetalleApiService.obtenerDetalle(id, admin);
            if (!"FINALIZADO".equals(detalle.getEstado())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se pueden descargar justificantes de expedientes finalizados");
            }
            List<Documento> documentos = documentoRepository.findByExpedienteId(id).stream()
                    .filter(documento -> esJustificanteDgt(documento)
                            || documento.getTipoDocumento() == TipoDocumento.MODELO_620)
                    .sorted(Comparator.comparing(Documento::getTipoDocumento))
                    .toList();
            boolean tieneHuella = documentos.stream().anyMatch(this::esJustificanteDgt);
            boolean tieneModelo = documentos.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MODELO_620);
            if (!tieneHuella || !tieneModelo) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan justificantes finales en el expediente " + id);
            }
            documentosPorCarpeta.put(carpetaZipExpediente(detalle, id, documentosPorCarpeta.keySet()), documentos);
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"justificantes_finales.zip\"");
        Path rutaBase = Paths.get(uploadDir).normalize().toAbsolutePath();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (Map.Entry<String, List<Documento>> entry : documentosPorCarpeta.entrySet()) {
                for (Documento documento : entry.getValue()) {
                    Path rutaArchivo = rutaBase.resolve(documento.getNombreArchivo()).normalize();
                    if (!rutaArchivo.startsWith(rutaBase) || !Files.exists(rutaArchivo)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se encontro un justificante en disco");
                    }
                    String nombreZip = entry.getKey() + "/" + nombreSeguro(documento.getNombreArchivoOriginal());
                    zip.putNextEntry(new ZipEntry(nombreZip));
                    Files.copy(rutaArchivo, zip);
                    zip.closeEntry();
                }
            }
        }
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

    @PostMapping("/{id}/informacion-adicional")
    public ResponseEntity<Void> solicitarInformacionAdicional(
            @PathVariable Long id,
            @RequestParam(required = false) String contenido,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication authentication
    ) {
        Usuario admin = requireAdmin(authentication);
        String contenidoFinal = contenido != null ? contenido : body != null ? body.get("contenido") : null;
        mensajeService.a\u00f1adirAExpediente(id, contenidoFinal, admin);
        expedienteService.cambiarEstado(id, EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/informacion-adicional/revisar")
    public ResponseEntity<Void> revisarInformacionAdicional(
            @PathVariable Long id,
            Authentication authentication
    ) {
        expedienteService.cambiarEstado(id, EstadoExpediente.EN_TRAMITE, requireAdmin(authentication));
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
        dto.setNombre(TextNormalizer.upperOrNull(source.getNombre()));
        dto.setDni(TextNormalizer.upperOrNull(source.getDni()));
        dto.setTelefono(TextNormalizer.upperOrNull(source.getTelefono()));
        dto.setDireccion(TextNormalizer.upperOrNull(source.getDireccion()));
        dto.setRol(source.getRol());
        return dto;
    }

    private List<InteresadoFormDto> mapInteresados(ActualizarExpedienteRequest request) {
        if (request.getInteresados() == null || request.getInteresados().isEmpty()) {
            return List.of(mapInteresado(request, 0), mapInteresado(request, 1));
        }
        return request.getInteresados().stream().map(interesado -> {
            InteresadoFormDto dto = new InteresadoFormDto();
            dto.setNombre(TextNormalizer.upperOrNull(interesado.getNombre()));
            dto.setDni(TextNormalizer.upperOrNull(interesado.getDni()));
            dto.setTelefono(TextNormalizer.upperOrNull(interesado.getTelefono()));
            dto.setDireccion(TextNormalizer.upperOrNull(interesado.getDireccion()));
            dto.setRol(interesado.getRol());
            return dto;
        }).toList();
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
                .siguientePasoTitulo(detalle.getSiguientePaso() != null ? detalle.getSiguientePaso().getTitulo() : null)
                .siguienteAccion(siguienteAccion)
                .justificantesFinalesDisponibles(tieneJustificantesFinales(expediente.getId(), detalle.getEstado()))
                .build();
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

    private boolean tieneJustificantesFinales(Long expedienteId, String estado) {
        if (!"FINALIZADO".equals(estado)) {
            return false;
        }
        List<Documento> documentos = documentoRepository.findByExpedienteId(expedienteId);
        return documentos.stream().anyMatch(this::esJustificanteDgt)
                && documentos.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MODELO_620);
    }

    private boolean esJustificanteDgt(Documento documento) {
        return documento.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE
                || documento.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT;
    }

    private String carpetaZipExpediente(ExpedienteDetailResponse detalle, Long expedienteId, Set<String> carpetasUsadas) {
        String base = detalle.getMatricula() != null && !detalle.getMatricula().isBlank()
                ? nombreSeguro(detalle.getMatricula())
                : "EXP-" + expedienteId;
        return carpetasUsadas.contains(base) ? base + "-EXP-" + expedienteId : base;
    }

    private String nombreSeguro(String nombre) {
        String base = nombre != null && !nombre.isBlank() ? nombre : "documento.pdf";
        return base.replaceAll("[\\\\/:*?\"<>|]", "_");
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

    private LocalDateTime fechaReferencia(LocalDateTime fechaCreacion, LocalDateTime fechaUltimaModificacion) {
        return fechaUltimaModificacion != null ? fechaUltimaModificacion : fechaCreacion;
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }
}
