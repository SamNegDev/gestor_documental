package com.example.gestor_documental.controller;

import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/incidencias")
@RequiredArgsConstructor
public class IncidenciaController {

    private final IncidenciaService incidenciaService;
    private final UsuarioService usuarioService;

    @PostMapping("/expediente/{id}/crear")
    public String crearIncidenciaExpediente(@PathVariable Long id,
                                            @RequestParam Long tipoIncidenciaId,
                                            @RequestParam String observaciones,
                                            Authentication authentication,
                                            RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = usuarioService.buscarPorEmail(authentication.getName());
            incidenciaService.crearIncidenciaExpediente(id, tipoIncidenciaId, observaciones, admin);
            redirectAttributes.addFlashAttribute("success", "Incidencia registrada correctamente en el expediente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/expedientes/" + id;
    }

    @PostMapping("/solicitud/{id}/crear")
    public String crearIncidenciaSolicitud(@PathVariable Long id,
                                           @RequestParam Long tipoIncidenciaId,
                                           @RequestParam String observaciones,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {
        try {
            Usuario admin = usuarioService.buscarPorEmail(authentication.getName());
            incidenciaService.crearIncidenciaSolicitud(id, tipoIncidenciaId, observaciones, admin);
            redirectAttributes.addFlashAttribute("success", "Incidencia registrada correctamente en la solicitud.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/solicitudes/" + id;
    }

    @PostMapping("/expediente/{id}/solicitar-revision")
    public String solicitarRevisionExpediente(@PathVariable Long id,
                                              Authentication authentication,
                                              RedirectAttributes redirectAttributes) {
        try {
            Usuario cliente = usuarioService.buscarPorEmail(authentication.getName());
            incidenciaService.solicitarRevisionExpediente(id, cliente);
            redirectAttributes.addFlashAttribute("success", "Revisión solicitada correctamente. Verificaremos sus cambios pronto.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/expedientes/" + id;
    }

    @PostMapping("/solicitud/{id}/solicitar-revision")
    public String solicitarRevisionSolicitud(@PathVariable Long id,
                                             Authentication authentication,
                                             RedirectAttributes redirectAttributes) {
        try {
            Usuario cliente = usuarioService.buscarPorEmail(authentication.getName());
            incidenciaService.solicitarRevisionSolicitud(id, cliente);
            redirectAttributes.addFlashAttribute("success", "Revisión solicitada correctamente. Verificaremos sus cambios pronto.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/solicitudes/" + id;
    }

    @PostMapping("/{id}/resolver")
    public String resolverIncidencia(@PathVariable Long id,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes,
                                     jakarta.servlet.http.HttpServletRequest request) {
        try {
            Usuario admin = usuarioService.buscarPorEmail(authentication.getName());
            incidenciaService.resolverIncidencia(id, admin);
            redirectAttributes.addFlashAttribute("success", "Incidencia marcada como resuelta.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/"; 
    }
}
