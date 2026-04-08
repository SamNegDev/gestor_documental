package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.gestor_documental.model.Solicitud;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/solicitudes")

public class SolicitudController {

    private final UsuarioService usuarioService;
    private final SolicitudService solicitudService;

    @GetMapping
    public String listarSolicitudes(Authentication authentication, Model model) {
        String email = authentication.getName();
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        List<Solicitud> solicitudes;

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            solicitudes = solicitudService.listarTodas();
        } else {

            if (usuarioLogueado.getCliente() == null) {
                solicitudes = List.of();
            } else {
                solicitudes = solicitudService.listarPorClienteId(usuarioLogueado.getCliente().getId());
            }

        }

        model.addAttribute("solicitudes", solicitudes);
        model.addAttribute("usuarioLogueado", usuarioLogueado);
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
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        String email = authentication.getName();

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        if (!solicitudService.tienePermisoSolicitud(solicitud, usuarioLogueado)) {
            return "redirect:/solicitudes";
        }


        model.addAttribute("solicitud", solicitud);
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("titulo", "Solicitud de Expediente");
        model.addAttribute("subtitulo", "Detalle de Solicitud de Expediente");
        model.addAttribute("tiposDocumento", TipoDocumento.values());


        return "solicitudes/detalle";
    }
    


}

