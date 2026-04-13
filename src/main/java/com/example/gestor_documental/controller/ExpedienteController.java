package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/expedientes")
public class ExpedienteController {

    private final ExpedienteService expedienteService;
    private final UsuarioService usuarioService;
    private final DocumentoService documentoService;


    @GetMapping
    public String listarExpedientes(Authentication authentication, Model model) {

        String email = authentication.getName();

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        List<Expediente> expedientes;

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            expedientes = expedienteService.listarTodos();
        } else {

            if (usuarioLogueado.getCliente() == null) {
                expedientes = List.of();
            } else {
                expedientes = expedienteService.listarPorClienteId(usuarioLogueado.getCliente().getId());
            }

        }

        model.addAttribute("expedientes", expedientes);
        model.addAttribute("usuarioLogueado", usuarioLogueado);


        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            model.addAttribute("titulo", "Listado Expedientes");
            model.addAttribute("subtitulo", "Gestion y consulta de expedientes de clientes");
            return "admin/lista_expedientes";
        } else {
            model.addAttribute("titulo", "Mis expedientes");
            model.addAttribute("subtitulo", "Mis expedientes");
            return "cliente/lista_expedientes";
        }
    }

    @GetMapping("/{id}")
    public String verDetalleExpediente(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        Expediente expediente = expedienteService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Expediente no encontrado"));

        String email = authentication.getName();

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        if (!expedienteService.tienePermisoExpediente(expediente, usuarioLogueado)) {
            return "redirect:/expedientes";
        }


        List<Documento> documentos = documentoService.listarPorExpediente(id);


        model.addAttribute("expediente", expediente);
        model.addAttribute("documentos", documentos);
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("titulo", "Mis expedientes");
        model.addAttribute("subtitulo", "Gestiona y consulta tus expedientes");
        model.addAttribute("tiposDocumento", TipoDocumento.values());


        return "expedientes/detalle";
    }


    @PostMapping("/{id}/estado")
    public String cambiarEstadoExpediente(@PathVariable Long id,
                                          @RequestParam EstadoExpediente nuevoEstado,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        try {
            expedienteService.cambiarEstado(id, nuevoEstado, usuarioLogueado);
            redirectAttributes.addFlashAttribute("success", "Estado del expediente actualizado correctamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/expedientes/" + id;
    }
}