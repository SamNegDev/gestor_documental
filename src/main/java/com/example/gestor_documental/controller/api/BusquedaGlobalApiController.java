package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.busqueda.BusquedaGlobalItemResponse;
import com.example.gestor_documental.dto.busqueda.BusquedaGlobalResponse;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/busqueda-global")
@RequiredArgsConstructor
public class BusquedaGlobalApiController {
    private final CurrentUserService currentUserService;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final InteresadoRepository interesadoRepository;

    @GetMapping
    public BusquedaGlobalResponse buscar(@RequestParam String q, Authentication authentication) {
        Usuario usuario = usuario(authentication);
        String termino = q != null ? q.trim().toUpperCase(Locale.ROOT) : "";
        if (termino.length() < 2) return BusquedaGlobalResponse.vacia();

        Long clienteId = usuario.getRolUsuario() == RolUsuario.ADMIN ? null
                : usuario.getCliente() != null ? usuario.getCliente().getId() : -1L;
        String texto = "%" + termino + "%";
        String digitos = termino.replaceAll("[^0-9]", "");
        String identificador = digitos.isBlank() ? "__SIN_ID__" : "%" + digitos + "%";
        List<Expediente> expedientes = expedienteRepository.buscarGlobal(clienteId, texto, identificador, PageRequest.of(0, 8));
        List<Solicitud> solicitudes = solicitudRepository.buscarGlobal(clienteId, texto, identificador, PageRequest.of(0, 6));
        List<Interesado> interesados = interesadoRepository.buscarGlobal(clienteId, texto, PageRequest.of(0, 6));

        List<BusquedaGlobalItemResponse> expedienteItems = expedientes.stream().limit(6).map(e -> mapExpediente(e, usuario)).toList();
        List<BusquedaGlobalItemResponse> solicitudItems = solicitudes.stream().map(this::mapSolicitud).toList();
        List<BusquedaGlobalItemResponse> interesadoItems = interesados.stream().map(this::mapInteresado).toList();
        LinkedHashMap<String, Expediente> vehiculos = new LinkedHashMap<>();
        expedientes.stream().filter(e -> e.getMatricula() != null && !e.getMatricula().isBlank())
                .forEach(e -> vehiculos.putIfAbsent(e.getMatricula().toUpperCase(Locale.ROOT), e));
        List<BusquedaGlobalItemResponse> vehiculoItems = vehiculos.entrySet().stream().limit(6)
                .map(entry -> new BusquedaGlobalItemResponse("VEH-" + entry.getKey(), entry.getKey(),
                        "Vehiculo relacionado", "Ultimo expediente EXP-" + entry.getValue().getId(),
                        "/vehiculos/" + entry.getKey())).toList();
        return new BusquedaGlobalResponse(expedienteItems, solicitudItems, interesadoItems, vehiculoItems);
    }

    private BusquedaGlobalItemResponse mapExpediente(Expediente expediente, Usuario usuario) {
        String matricula = valor(expediente.getMatricula(), "SIN MATRICULA");
        String tipo = expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null ? expediente.getTipoTramite().getNombre().name().replace('_', ' ') : "TRAMITE";
        String estado = expediente.getEstadoExpediente() != null ? expediente.getEstadoExpediente().name().replace('_', ' ') : "SIN ESTADO";
        String enlace = usuario.getRolUsuario() == RolUsuario.CLIENTE ? "/cliente/expedientes/" + expediente.getId() : "/expedientes/" + expediente.getId();
        return new BusquedaGlobalItemResponse("EXP-" + expediente.getId(), matricula, tipo, "EXP-" + expediente.getId() + " · " + estado, enlace);
    }

    private BusquedaGlobalItemResponse mapSolicitud(Solicitud solicitud) {
        String nombre = valor(solicitud.getInteresado1Nombre(), "SIN INTERESADO");
        String estado = solicitud.getEstadoSolicitud() != null ? solicitud.getEstadoSolicitud().name().replace('_', ' ') : "SIN ESTADO";
        return new BusquedaGlobalItemResponse("SOL-" + solicitud.getId(), valor(solicitud.getMatricula(), "SIN MATRICULA"), nombre,
                "SOL-" + solicitud.getId() + " · " + estado, "/solicitudes/" + solicitud.getId());
    }

    private BusquedaGlobalItemResponse mapInteresado(Interesado interesado) {
        return new BusquedaGlobalItemResponse("INT-" + interesado.getId(), interesado.getNombre(), interesado.getDni(),
                interesado.getTipoPersona() != null ? interesado.getTipoPersona().name() : "INTERESADO", "/interesados/" + interesado.getId());
    }

    private Usuario usuario(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }

    private String valor(String valor, String alternativa) { return valor != null && !valor.isBlank() ? valor : alternativa; }
}
