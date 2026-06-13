package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.registro.InteresadoRegistroResponse;
import com.example.gestor_documental.dto.registro.TramiteRegistroResponse;
import com.example.gestor_documental.dto.registro.VehiculoRegistroResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final UsuarioService usuarioService;
    private final InteresadoRepository interesadoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final ExpedienteInteresadoRepository relacionRepository;

    @GetMapping("/interesados")
    public List<InteresadoRegistroResponse> listarInteresados(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ESTE_MES") String periodo,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            Authentication authentication
    ) {
        Usuario usuario = usuario(authentication);
        String query = normalizar(q);
        DateRange range = dateRange(periodo, fechaDesde, fechaHasta);
        Long clienteId = clienteIdVisible(usuario);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN && clienteId == null) return List.of();

        List<ExpedienteInteresado> relaciones = relacionRepository.findRegistro(clienteId, range.desde(), range.hasta());
        Map<Long, List<ExpedienteInteresado>> relacionesPorInteresado = relaciones.stream()
                .collect(java.util.stream.Collectors.groupingBy(relacion -> relacion.getInteresado().getId()));
        List<Interesado> interesados = usuario.getRolUsuario() == RolUsuario.ADMIN && "TODO".equals(periodo)
                ? interesadoRepository.findAll()
                : relacionesPorInteresado.values().stream().map(items -> items.get(0).getInteresado()).toList();

        return interesados.stream()
                .filter(interesado -> query == null
                        || contiene(interesado.getNombre(), query)
                        || contiene(interesado.getDni(), query))
                .map(interesado -> mapInteresado(interesado, relacionesPorInteresado.getOrDefault(interesado.getId(), List.of())))
                .sorted(Comparator.comparing(InteresadoRegistroResponse::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @GetMapping("/interesados/{id}")
    public InteresadoRegistroResponse detalleInteresado(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = usuario(authentication);
        Interesado interesado = interesadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Interesado no encontrado"));
        List<ExpedienteInteresado> relaciones = tramitesInteresado(id, usuario);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN && relaciones.isEmpty()) {
            throw new AccesoDenegadoException("No tienes permiso para consultar este interesado");
        }
        return mapInteresado(interesado, relaciones);
    }

    @GetMapping("/vehiculos")
    public List<VehiculoRegistroResponse> listarVehiculos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ESTE_MES") String periodo,
            @RequestParam(required = false) LocalDate fechaDesde,
            @RequestParam(required = false) LocalDate fechaHasta,
            Authentication authentication
    ) {
        Usuario usuario = usuario(authentication);
        String query = normalizar(q);
        Map<String, List<Expediente>> porMatricula = expedientesVisibles(usuario, periodo, fechaDesde, fechaHasta).stream()
                .filter(expediente -> normalizar(expediente.getMatricula()) != null)
                .filter(expediente -> query == null || contiene(expediente.getMatricula(), query))
                .collect(java.util.stream.Collectors.groupingBy(
                        expediente -> normalizar(expediente.getMatricula()),
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
                .filter(expediente -> matriculaNormalizada != null && matriculaNormalizada.equals(normalizar(expediente.getMatricula())))
                .toList();
        if (expedientes.isEmpty()) {
            throw new RecursoNoEncontradoException("Vehiculo no encontrado");
        }
        return mapVehiculo(matriculaNormalizada, expedientes, usuario);
    }

    private InteresadoRegistroResponse mapInteresado(Interesado interesado, List<ExpedienteInteresado> relaciones) {
        List<TramiteRegistroResponse> tramites = relaciones.stream()
                .map(relacion -> mapTramite(relacion.getExpediente(), relacion.getRol() != null ? relacion.getRol().name() : null))
                .sorted(Comparator.comparing(TramiteRegistroResponse::getFechaUltimaModificacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return InteresadoRegistroResponse.builder()
                .id(interesado.getId()).dni(interesado.getDni()).nombre(interesado.getNombre())
                .telefono(interesado.getTelefono()).direccion(interesado.getDireccion())
                .tipoPersona(interesado.getTipoPersona() != null ? interesado.getTipoPersona().name() : null)
                .totalTramites(tramites.size())
                .ultimaActividad(tramites.isEmpty() ? null : tramites.get(0).getFechaUltimaModificacion())
                .tramites(tramites).build();
    }

    private VehiculoRegistroResponse mapVehiculo(String matricula, List<Expediente> expedientes, Usuario usuario) {
        List<TramiteRegistroResponse> tramites = expedientes.stream().map(expediente -> mapTramite(expediente, null))
                .sorted(Comparator.comparing(TramiteRegistroResponse::getFechaUltimaModificacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<String> interesados = expedientes.stream()
                .flatMap(expediente -> relacionRepository.findByExpedienteId(expediente.getId()).stream())
                .map(ExpedienteInteresado::getInteresado)
                .filter(java.util.Objects::nonNull)
                .map(interesado -> interesado.getDni() + " - " + interesado.getNombre())
                .distinct().toList();
        return VehiculoRegistroResponse.builder().matricula(matricula).totalTramites(tramites.size())
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
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario == null) throw new AccesoDenegadoException("Usuario no encontrado");
        return usuario;
    }

    private LocalDateTime fechaReferencia(Expediente expediente) {
        return expediente.getFechaUltimaModificacion() != null ? expediente.getFechaUltimaModificacion() : expediente.getFechaCreacion();
    }

    private String formatear(LocalDateTime fecha) { return fecha != null ? fecha.format(DATE_FORMAT) : null; }
    private DateRange dateRange(String periodo, LocalDate fechaDesde, LocalDate fechaHasta) {
        LocalDate today = LocalDate.now();
        return switch (periodo != null ? periodo : "ESTE_MES") {
            case "ULTIMA_SEMANA" -> new DateRange(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
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
