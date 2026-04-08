package com.example.gestor_documental.controller;

import com.example.gestor_documental.dto.DocumentoFormDto;
import com.example.gestor_documental.dto.DocumentoFormWrapper;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
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
    private final DocumentoService documentoService;

    @GetMapping("/nuevo")
    public String nuevaSolicitud(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        if (usuarioLogueado.getCliente() == null) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes un cliente asociado. Contacta con administración.");
            return "redirect:/solicitudes";
        }

        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("solicitud", new Solicitud());
        model.addAttribute("cliente", usuarioLogueado.getCliente());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("documentosWrapper", new DocumentoFormWrapper());
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("titulo", "Nueva Solicitud");
        model.addAttribute("subtitulo", "Registro de nueva solicitud");

        return "cliente/solicitud-form";
    }

    @PostMapping
    public String guardarSolicitud(@ModelAttribute("solicitud") Solicitud solicitud,
                                   @ModelAttribute DocumentoFormWrapper documentosWrapper,
                                   @RequestParam Long tipoTramiteId,
                                   Authentication authentication,
                                   Model model) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        if (usuarioLogueado.getCliente() == null) {
            throw new IllegalStateException("El usuarioLogueado no tiene cliente asociado");
        }

        Cliente cliente = usuarioLogueado.getCliente();

        try {
            Solicitud solicitudGuardada = solicitudService.crearSolicitudCompleta(
                    solicitud, usuarioLogueado,
                    cliente,
                    tipoTramiteId
            );

            if (documentosWrapper.getDocumentos() != null) {
                for (DocumentoFormDto doc : documentosWrapper.getDocumentos()) {
                    if (doc.getArchivo() != null && !doc.getArchivo().isEmpty()) {
                        documentoService.guardarParaSolicitud(
                                solicitudGuardada.getId(),
                                doc.getArchivo(),
                                doc.getTipoDocumento(),
                                usuarioLogueado
                        );
                    }
                }
            }

            return "redirect:/solicitudes/" + solicitudGuardada.getId();

        } catch (IllegalArgumentException e) {
            model.addAttribute("usuarioLogueado", usuarioLogueado);
            model.addAttribute("solicitud", solicitud);
            model.addAttribute("cliente", cliente);
            model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
            model.addAttribute("tipoTramiteId", tipoTramiteId);
            model.addAttribute("documentosWrapper", new DocumentoFormWrapper());
            model.addAttribute("tiposDocumento", TipoDocumento.values());
            model.addAttribute("titulo", "Nueva Solicitud");
            model.addAttribute("subtitulo", "Registro de nueva solicitud");
            model.addAttribute("error", e.getMessage());

            return "cliente/solicitud-form";
        }
    }
}