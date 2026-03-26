package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cliente/solicitudes")
public class ClienteSolicitudController {

    private final SolicitudService solicitudService;
    private final UsuarioService usuarioService;
    private final TipoTramiteService tipoTramiteService;

    @GetMapping("/nuevo")
    public String nuevaSolicitud(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        if (usuario.getCliente() == null) {

            redirectAttributes.addFlashAttribute("error",
                    "No tienes un cliente asociado. Contacta con administración.");
            return "redirect:/solicitudes";
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("solicitud", new Solicitud());
        model.addAttribute("cliente", usuario.getCliente());
        model.addAttribute("titulo", "Nueva Solicitud");
        model.addAttribute("subtitulo", "Registro de nueva solicitud");
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());

        return "cliente/solicitud-form";
    }

    @PostMapping
    public String guardarSolicitud(@ModelAttribute("solicitud") Solicitud solicitud,
                                   Authentication authentication,
                                   @RequestParam Long tipoTramiteId
            , Model model) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario.getCliente() == null) {
            throw new IllegalStateException("El usuario no tiene cliente asociado");
        }
        Cliente cliente = usuario.getCliente();


        try {
            Solicitud solicitudGuardada = solicitudService.crearSolicitudCompleta(
                    solicitud,
                    cliente,
                    tipoTramiteId
            );

            return "redirect:/solicitudes/" + solicitudGuardada.getId();

        } catch (IllegalArgumentException e) {

            model.addAttribute("usuario", usuario);
            model.addAttribute("solicitud", solicitud);
            model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
            model.addAttribute("tipoTramiteId", tipoTramiteId);
            model.addAttribute("titulo", "Nueva Solicitud");
            model.addAttribute("subtitulo", "Registro de nueva solicitud");
            model.addAttribute("error", e.getMessage());

            return "redirect:cliente/solicitud-form";
        }

    }
}
