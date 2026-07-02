package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.DocumentoExpedienteResponse;
import com.example.gestor_documental.dto.registro.InteresadoHabitualRequest;
import com.example.gestor_documental.dto.registro.InteresadoRegistroResponse;
import com.example.gestor_documental.dto.registro.InteresadoUpdateRequest;
import com.example.gestor_documental.dto.registro.TramiteRegistroResponse;
import com.example.gestor_documental.dto.registro.VehiculoRegistroResponse;
import com.example.gestor_documental.dto.registro.VehiculoUpdateRequest;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.ClienteInteresado;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.Vehiculo;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.InteresadoService;
import com.example.gestor_documental.service.VehiculoService;
import com.example.gestor_documental.repository.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/registro")
@RequiredArgsConstructor
public class RegistroApiController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CurrentUserService currentUserService;
    private final InteresadoRepository interesadoRepository;
    private final ClienteInteresadoRepository clienteInteresadoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final ExpedienteInteresadoRepository relacionRepository;
    private final VehiculoRepository vehiculoRepository;
    private final InteresadoService interesadoService;
    private final ClienteService clienteService;
    private final DocumentoService documentoService;
    private final VehiculoService vehiculoService;

    @GetMapping("/interesados")
    public List<InteresadoRegistroResponse> listarInteresados(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ULTIMA_SEMANA") String periodo,
            @RequestParam(required = false, defaultValue = "RECIENTES") String vista,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            Authentication authentication
    ) {
        Usuario usuario = usuario(authentication);
        String query = normalizar(q);
        String vistaNormalizada = vista != null ? vista.toUpperCase(Locale.ROOT) : "RECIENTES";
        boolean soloHabituales = "HABITUALES".equals(vistaNormalizada);
        boolean incluirHabituales = soloHabituales || "TODOS".equals(vistaNormalizada);
        DateRange range = dateRange(periodo, fechaDesde, fechaHasta);
        Long clienteId = clienteIdVisible(usuario);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN && clienteId == null) return List.of();

        List<ExpedienteInteresado> relaciones = soloHabituales
                ? relacionRepository.findRegistro(clienteId, null, null)
                : relacionRepository.findRegistro(clienteId, range.desde(), range.hasta());
        Map<Long, List<ExpedienteInteresado>> relacionesPorInteresado = relaciones.stream()
                .collect(java.util.stream.Collectors.groupingBy(relacion -> relacion.getInteresado().getId()));
        Map<Long, ClienteInteresado> habituales = relacionesHabitualesVisibles(usuario, clienteId);
        List<Interesado> interesadosBase;
        if (soloHabituales) {
            interesadosBase = habituales.values().stream().map(ClienteInteresado::getInteresado).toList();
        } else if (usuario.getRolUsuario() == RolUsuario.ADMIN && "TODO".equals(periodo)) {
            interesadosBase = interesadoRepository.findAll();
        } else {
            interesadosBase = relacionesPorInteresado.values().stream().map(items -> items.get(0).getInteresado()).toList();
        }
        Map<Long, Interesado> interesadosPorId = new LinkedHashMap<>();
        interesadosBase.forEach(interesado -> interesadosPorId.put(interesado.getId(), interesado));
        if (incluirHabituales) {
            habituales.values().forEach(relacion -> interesadosPorId.put(relacion.getInteresado().getId(), relacion.getInteresado()));
        }

        return interesadosPorId.values().stream()
                .filter(interesado -> query == null
                        || contiene(interesado.getNombre(), query)
                        || contiene(interesado.getDni(), query))
                .map(interesado -> mapInteresado(interesado, relacionesPorInteresado.getOrDefault(interesado.getId(), List.of()), habituales.get(interesado.getId()), clienteId))
                .sorted(Comparator.comparing(InteresadoRegistroResponse::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @GetMapping("/interesados/{id}")
    public InteresadoRegistroResponse detalleInteresado(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = usuario(authentication);
        Interesado interesado = interesadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"));
        List<ExpedienteInteresado> relaciones = tramitesInteresado(id, usuario);
        Long clienteId = clienteIdVisible(usuario);
        ClienteInteresado habitual = clienteId != null
                ? clienteInteresadoRepository.findByClienteIdAndInteresadoId(clienteId, id).orElse(null)
                : clienteInteresadoRepository.findByHabitualTrueOrderByInteresadoNombreAsc().stream()
                .filter(relacion -> relacion.getInteresado() != null && id.equals(relacion.getInteresado().getId()))
                .findFirst().orElse(null);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN && relaciones.isEmpty() && habitual == null) {
            throw new AccesoDenegadoException("No tienes permiso para consultar este interesado");
        }
        return mapInteresado(interesado, relaciones, habitual, clienteId);
    }

    @PostMapping("/interesados")
    public InteresadoRegistroResponse crearInteresadoHabitual(@RequestBody InteresadoHabitualRequest request,
                                                              Authentication authentication) {
        Usuario usuario = usuario(authentication);
        Long clienteId = clienteIdVisible(usuario);
        if (clienteId == null) {
            throw new AccesoDenegadoException("Solo un usuario cliente puede crear interesados habituales");
        }
        String dni = normalizar(request.dni());
        String nombre = request.nombre() != null ? request.nombre().trim() : null;
        if (dni == null || nombre == null || nombre.isBlank()) {
            throw new OperacionInvalidaException("DNI/CIF y nombre son obligatorios");
        }
        Interesado interesado = interesadoRepository.findByDni(dni).orElseGet(() -> {
            Interesado nuevo = new Interesado();
            nuevo.setDni(dni);
            nuevo.setNombre(request.nombre());
            nuevo.setTelefono(request.telefono());
            nuevo.setDireccion(request.direccion());
            nuevo.setTipoVia(request.tipoVia());
            nuevo.setNombreVia(request.nombreVia());
            nuevo.setCodigoPostal(request.codigoPostal());
            nuevo.setMunicipio(request.municipio());
            nuevo.setProvincia(request.provincia());
            nuevo.setTipoPersona(request.tipoPersona());
            return interesadoService.guardar(nuevo);
        });
        ClienteInteresado habitual = clienteInteresadoRepository.findByClienteIdAndInteresadoId(clienteId, interesado.getId())
                .orElseGet(() -> {
                    ClienteInteresado relacion = new ClienteInteresado();
                    relacion.setCliente(clienteService.buscarPorId(clienteId).orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado")));
                    relacion.setInteresado(interesado);
                    return relacion;
                });
        habitual.setHabitual(true);
        clienteInteresadoRepository.save(habitual);
        return mapInteresado(interesado, tramitesInteresado(interesado.getId(), usuario), habitual, clienteId);
    }

    @PostMapping("/interesados/{id}/habitual")
    public InteresadoRegistroResponse marcarInteresadoHabitual(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = usuario(authentication);
        Long clienteId = clienteIdVisible(usuario);
        if (clienteId == null) {
            throw new AccesoDenegadoException("Solo un usuario cliente puede marcar clientes habituales");
        }
        Interesado interesado = interesadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"));
        ClienteInteresado habitual = clienteInteresadoRepository.findByClienteIdAndInteresadoId(clienteId, id)
                .orElseGet(() -> {
                    ClienteInteresado relacion = new ClienteInteresado();
                    relacion.setCliente(clienteService.buscarPorId(clienteId).orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado")));
                    relacion.setInteresado(interesado);
                    return relacion;
                });
        habitual.setHabitual(true);
        clienteInteresadoRepository.save(habitual);
        return mapInteresado(interesado, tramitesInteresado(id, usuario), habitual, clienteId);
    }

    @PostMapping(value = "/interesados/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InteresadoRegistroResponse subirDocumentoInteresadoHabitual(@PathVariable Long id,
                                                                       @RequestParam("archivo") MultipartFile archivo,
                                                                       @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
                                                                       Authentication authentication) {
        Usuario usuario = usuario(authentication);
        Long clienteId = clienteIdVisible(usuario);
        if (clienteId == null || !clienteInteresadoRepository.existsByClienteIdAndInteresadoIdAndHabitualTrue(clienteId, id)) {
            throw new AccesoDenegadoException("El interesado no pertenece a tu cartera habitual");
        }
        validarPdf(archivo);
        documentoService.guardarParaInteresadoHabitual(clienteId, id, archivo, tipoDocumento, usuario);
        Interesado interesado = interesadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"));
        ClienteInteresado habitual = clienteInteresadoRepository.findByClienteIdAndInteresadoId(clienteId, id).orElse(null);
        return mapInteresado(interesado, tramitesInteresado(id, usuario), habitual, clienteId);
    }

    @PutMapping("/interesados/{id}")
    public void actualizarInteresado(@PathVariable Long id,
                                     @RequestBody InteresadoUpdateRequest request,
                                     Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        interesadoService.actualizar(id, request);
    }

    @GetMapping("/vehiculos")
    public List<VehiculoRegistroResponse> listarVehiculos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ULTIMA_SEMANA") String periodo,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            Authentication authentication
    ) {
        Usuario usuario = usuario(authentication);
        String query = normalizar(q);
        Map<String, List<Expediente>> porMatricula = expedientesVisibles(usuario, periodo, fechaDesde, fechaHasta).stream()
                .filter(expediente -> matriculaRegistro(expediente) != null)
                .filter(expediente -> query == null || contiene(matriculaRegistro(expediente), query))
                .collect(java.util.stream.Collectors.groupingBy(
                        this::matriculaRegistro,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        return porMatricula.entrySet().stream()
                .map(entry -> mapVehiculo(entry.getKey(), entry.getValue(), usuario))
                .sorted(Comparator.comparing(VehiculoRegistroResponse::getUltimaActividad, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/vehiculos/{matricula}")
    public VehiculoRegistroResponse detalleVehiculo(@PathVariable String matricula, Authentication authentication) {
        Usuario usuario = usuario(authentication);
        String matriculaNormalizada = normalizar(matricula);
        List<Expediente> expedientes = expedientesVisibles(usuario).stream()
                .filter(expediente -> matriculaNormalizada != null && matriculaNormalizada.equals(matriculaRegistro(expediente)))
                .toList();
        if (expedientes.isEmpty()) {
            throw new RecursoNoEncontradoException("Vehiculo no encontrado");
        }
        return mapVehiculo(matriculaNormalizada, expedientes, usuario);
    }

    @PutMapping("/vehiculos/{matricula}")
    public void actualizarVehiculo(@PathVariable String matricula,
                                   @RequestBody VehiculoUpdateRequest request,
                                   Authentication authentication) {
        Usuario usuario = usuario(authentication);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede editar la ficha del vehiculo");
        }
        vehiculoService.actualizarPorMatricula(matricula, request);
    }

    private InteresadoRegistroResponse mapInteresado(Interesado interesado, List<ExpedienteInteresado> relaciones, ClienteInteresado habitual, Long clienteId) {
        List<TramiteRegistroResponse> tramites = relaciones.stream()
                .map(relacion -> mapTramite(relacion.getExpediente(), relacion.getRol() != null ? relacion.getRol().name() : null))
                .sorted(Comparator.comparing(TramiteRegistroResponse::getFechaUltimaModificacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return InteresadoRegistroResponse.builder()
                .id(interesado.getId()).dni(interesado.getDni()).nombre(interesado.getNombre())
                .telefono(interesado.getTelefono()).direccion(interesado.getDireccion())
                .tipoVia(interesado.getTipoVia())
                .nombreVia(interesado.getNombreVia())
                .codigoPostal(interesado.getCodigoPostal())
                .municipio(interesado.getMunicipio())
                .provincia(interesado.getProvincia())
                .tipoPersona(interesado.getTipoPersona() != null ? interesado.getTipoPersona().name() : null)
                .habitual(habitual != null && Boolean.TRUE.equals(habitual.getHabitual()))
                .representanteLegal(habitual != null && Boolean.TRUE.equals(habitual.getRepresentanteLegal()))
                .totalTramites(tramites.size())
                .ultimaActividad(tramites.isEmpty() ? null : tramites.get(0).getFechaUltimaModificacion())
                .documentos(clienteId != null && habitual != null && Boolean.TRUE.equals(habitual.getHabitual())
                        ? documentoService.listarPorInteresadoHabitual(clienteId, interesado.getId()).stream().map(this::mapDocumento).toList()
                        : List.of())
                .tramites(tramites).build();
    }

    private Map<Long, ClienteInteresado> relacionesHabitualesVisibles(Usuario usuario, Long clienteId) {
        List<ClienteInteresado> relaciones = clienteId != null
                ? clienteInteresadoRepository.findByClienteIdAndHabitualTrueOrderByInteresadoNombreAsc(clienteId)
                : usuario.getRolUsuario() == RolUsuario.ADMIN
                ? clienteInteresadoRepository.findByHabitualTrueOrderByInteresadoNombreAsc()
                : List.of();
        return relaciones.stream()
                .filter(relacion -> relacion.getInteresado() != null && relacion.getInteresado().getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        relacion -> relacion.getInteresado().getId(),
                        relacion -> relacion,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private DocumentoExpedienteResponse mapDocumento(Documento documento) {
        return DocumentoExpedienteResponse.builder()
                .id(documento.getId())
                .nombre(documento.getNombreArchivo())
                .nombreOriginal(documento.getNombreArchivoOriginal())
                .tipo(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : null)
                .fechaSubida(documento.getFechaSubida() != null ? documento.getFechaSubida().toString() : null)
                .subidoPor(documento.getSubidoPor() != null ? documento.getSubidoPor().getNombre() : null)
                .interesadoId(documento.getInteresado() != null ? documento.getInteresado().getId() : null)
                .interesadoNombre(documento.getInteresado() != null ? documento.getInteresado().getNombre() : null)
                .estado("SUBIDO")
                .subido(true)
                .requeridoAhora(false)
                .build();
    }

    private VehiculoRegistroResponse mapVehiculo(String matricula, List<Expediente> expedientes, Usuario usuario) {
        Vehiculo vehiculo = vehiculoRepository.findByMatricula(matricula).orElse(null);
        List<TramiteRegistroResponse> tramites = expedientes.stream().map(expediente -> mapTramite(expediente, null))
                .sorted(Comparator.comparing(TramiteRegistroResponse::getFechaUltimaModificacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<String> interesados = expedientes.stream()
                .flatMap(expediente -> relacionRepository.findByExpedienteId(expediente.getId()).stream())
                .map(ExpedienteInteresado::getInteresado)
                .filter(java.util.Objects::nonNull)
                .map(interesado -> interesado.getDni() + " - " + interesado.getNombre())
                .distinct().toList();
        return VehiculoRegistroResponse.builder()
                .id(vehiculo != null ? vehiculo.getId() : null)
                .matricula(vehiculo != null ? vehiculo.getMatricula() : matricula)
                .bastidor(vehiculo != null ? vehiculo.getBastidor() : null)
                .marca(vehiculo != null ? vehiculo.getMarca() : null)
                .modelo(vehiculo != null ? vehiculo.getModelo() : null)
                .fechaPrimeraMatriculacion(vehiculo != null && vehiculo.getFechaPrimeraMatriculacion() != null
                        ? vehiculo.getFechaPrimeraMatriculacion().toString()
                        : null)
                .observaciones(vehiculo != null ? vehiculo.getObservaciones() : null)
                .totalTramites(tramites.size())
                .ultimaActividad(tramites.isEmpty() ? null : tramites.get(0).getFechaUltimaModificacion())
                .interesados(interesados).tramites(tramites).build();
    }

    private TramiteRegistroResponse mapTramite(Expediente expediente, String rol) {
        return TramiteRegistroResponse.builder().id(expediente.getId()).matricula(expediente.getMatricula())
                .tipoTramite(expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                        ? expediente.getTipoTramite().getNombre().name() : null)
                .estado(expediente.getEstadoExpediente() != null ? expediente.getEstadoExpediente().name() : null)
                .rol(rol).cliente(expediente.getCliente() != null ? expediente.getCliente().getNombre() : null)
                .fechaUltimaModificacion(formatear(fechaReferencia(expediente))).build();
    }

    private List<ExpedienteInteresado> tramitesInteresado(Long interesadoId, Usuario usuario) {
        Long clienteId = clienteIdVisible(usuario);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN && clienteId == null) return List.of();
        return relacionRepository.findRegistroByInteresadoId(interesadoId, clienteId);
    }

    private Long clienteIdVisible(Usuario usuario) {
        return usuario.getRolUsuario() == RolUsuario.ADMIN || usuario.getCliente() == null
                ? null
                : usuario.getCliente().getId();
    }

    private List<Expediente> expedientesVisibles(Usuario usuario) {
        return expedientesVisibles(usuario, "TODO", null, null);
    }

    private List<Expediente> expedientesVisibles(Usuario usuario, String periodo, LocalDate fechaDesde, LocalDate fechaHasta) {
        DateRange range = dateRange(periodo, fechaDesde, fechaHasta);
        if (usuario.getRolUsuario() == RolUsuario.ADMIN) return expedienteRepository.findByPeriodo(range.desde(), range.hasta());
        if (usuario.getCliente() == null) return List.of();
        return expedienteRepository.findByClienteIdAndPeriodo(usuario.getCliente().getId(), range.desde(), range.hasta());
    }

    private Usuario usuario(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }

    private void validarPdf(MultipartFile archivo) {
        String nombre = archivo != null ? archivo.getOriginalFilename() : null;
        if (archivo == null || archivo.isEmpty() || nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new OperacionInvalidaException("El documento debe ser un PDF");
        }
    }

    private LocalDateTime fechaReferencia(Expediente expediente) {
        return expediente.getFechaUltimaModificacion() != null ? expediente.getFechaUltimaModificacion() : expediente.getFechaCreacion();
    }

    private String formatear(LocalDateTime fecha) { return fecha != null ? fecha.format(DATE_FORMAT) : null; }
    private String matriculaRegistro(Expediente expediente) {
        if (expediente.getVehiculo() != null && expediente.getVehiculo().getMatricula() != null) {
            return expediente.getVehiculo().getMatricula();
        }
        return normalizar(expediente.getMatricula());
    }
    private DateRange dateRange(String periodo, LocalDate fechaDesde, LocalDate fechaHasta) {
        LocalDate today = LocalDate.now();
        return switch (periodo != null ? periodo : "ULTIMA_SEMANA") {
            case "ULTIMA_SEMANA" -> new DateRange(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "MES_ANTERIOR" -> new DateRange(today.minusMonths(1).withDayOfMonth(1).atStartOfDay(), today.withDayOfMonth(1).atStartOfDay());
            case "ULTIMOS_3_MESES" -> new DateRange(today.minusMonths(3).atStartOfDay(), null);
            case "ESTE_ANIO" -> new DateRange(today.withDayOfYear(1).atStartOfDay(), today.plusYears(1).withDayOfYear(1).atStartOfDay());
            case "TODO" -> new DateRange(null, null);
            case "PERSONALIZADO" -> fechaDesde != null && fechaHasta != null && !fechaDesde.isAfter(fechaHasta)
                    ? new DateRange(fechaDesde.atStartOfDay(), fechaHasta.plusDays(1).atStartOfDay())
                    : new DateRange(today.plusYears(100).atStartOfDay(), today.plusYears(100).atStartOfDay());
            default -> new DateRange(today.withDayOfMonth(1).atStartOfDay(), today.plusMonths(1).withDayOfMonth(1).atStartOfDay());
        };
    }
    private String normalizar(String valor) { return valor == null || valor.isBlank() ? null : valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT); }
    private boolean contiene(String valor, String query) { return valor != null && normalizar(valor) != null && normalizar(valor).contains(query); }
    private record DateRange(LocalDateTime desde, LocalDateTime hasta) {}
}
