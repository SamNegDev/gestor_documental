package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.example.gestor_documental.model.Solicitud;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.service.TipoIncidenciaService;
import com.example.gestor_documental.service.IncidenciaService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/solicitudes")

public class SolicitudController {

    private final UsuarioService usuarioService;
    private final SolicitudService solicitudService;
    private final TipoTramiteService tipoTramiteService;
    private final ClienteService clienteService;
    private final TipoIncidenciaService tipoIncidenciaService;
    private final IncidenciaService incidenciaService;

    @GetMapping
    public String listarSolicitudes(Authentication authentication, Model model,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) Long clienteId) {
        String email = authentication.getName();
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        List<Solicitud> solicitudes;

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            solicitudes = solicitudService.listarTodas();
            model.addAttribute("clientes", clienteService.listarTodos());

            if (clienteId != null) {
                solicitudes = solicitudes.stream()
                        .filter(s -> s.getCliente() != null && s.getCliente().getId().equals(clienteId))
                        .toList();
                model.addAttribute("clienteSeleccionado", clienteId);
            }
        } else {

            if (usuarioLogueado.getCliente() == null) {
                solicitudes = List.of();
            } else {
                solicitudes = solicitudService.listarPorClienteId(usuarioLogueado.getCliente().getId());
            }
        }
        if (estado != null) {
            solicitudes = solicitudes.stream()
                    .filter(s -> s.getEstadoSolicitud() == estado)
                    .toList();
        }
        if (tipoTramiteId != null) {
            solicitudes = solicitudes.stream()
                    .filter(s -> s.getTipoTramite() != null && s.getTipoTramite().getId().equals(tipoTramiteId))
                    .toList();
        }
        if (matricula != null) {
            String matriculaBusqueda = matricula.trim();

            if (!matriculaBusqueda.isEmpty()) {
                solicitudes = solicitudes.stream()
                        .filter(s -> s.getMatricula() != null &&
                                s.getMatricula().toLowerCase().contains(matriculaBusqueda.toLowerCase()))
                        .toList();
            }
        }
        boolean hayFiltrosActivos = estado != null
                || tipoTramiteId != null || clienteId != null
                || (matricula != null && !matricula.trim().isEmpty());

        model.addAttribute("hayFiltrosActivos", hayFiltrosActivos);
        model.addAttribute("solicitudes", solicitudes);
        model.addAttribute("estadosSolicitud", EstadoSolicitud.values());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("hayFiltrosActivos", hayFiltrosActivos);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("tipoTramiteIdSeleccionado", tipoTramiteId);
        model.addAttribute("matriculaSeleccionada", matricula);
        model.addAttribute("titulo", "Solicitudes");

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            model.addAttribute("subtitulo", "Gestión de Solicitudes de clientes");
            return "admin/lista_solicitudes";
        } else {
            model.addAttribute("subtitulo", "Mis Solicitudes");
            return "cliente/lista_solicitudes";
        }

    }

    @GetMapping("/{id}")
    public String verDetalleSolicitud(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        Solicitud solicitud = solicitudService.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));

        String email = authentication.getName();

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        if (!solicitudService.tienePermisoSolicitud(solicitud, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a esta solicitud");
        }

        List<Incidencia> incidencias = incidenciaService.listarPorSolicitud(id);

        model.addAttribute("solicitud", solicitud);
        model.addAttribute("incidencias", incidencias);
        model.addAttribute("tiposIncidencia", tipoIncidenciaService.listarTodosActivos());
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("titulo", "Solicitud de Expediente");
        model.addAttribute("subtitulo", "Detalle de Solicitud de Expediente");
        model.addAttribute("tiposDocumento", TipoDocumento.values());

        return "solicitudes/detalle";
    }

    @PostMapping("/{id}/convertir")
    public String convertirSolicitud(@PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        Usuario admin = usuarioService.buscarPorEmail(authentication.getName());

        try {
            Expediente expediente = solicitudService.convertirAExpediente(id, admin);
            redirectAttributes.addFlashAttribute("success", "Solicitud convertida a expediente correctamente");
            return "redirect:/expedientes/" + expediente.getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/solicitudes/" + id;
        }
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstadoSolicitud(@PathVariable Long id,
            @RequestParam EstadoSolicitud nuevoEstado,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        Usuario admin = usuarioService.buscarPorEmail(authentication.getName());

        try {
            solicitudService.cambiarEstadoSolicitud(id, nuevoEstado, admin);
            redirectAttributes.addFlashAttribute("success", "Estado actualizado correctamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/solicitudes/" + id;
    }

}
