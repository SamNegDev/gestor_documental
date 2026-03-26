package com.example.gestor_documental.controller;

import com.example.gestor_documental.dto.InteresadosFormDto;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/expedientes")
public class AdminExpedienteController {

    private final ExpedienteService expedienteService;
    private final UsuarioService usuarioService;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;

    @GetMapping("/nuevo")
    public String formularioNuevoCliente(Authentication authentication, Model model) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        model.addAttribute("usuario", usuario);
        model.addAttribute("expediente", new Expediente());
        model.addAttribute("interesadosForm", new InteresadosFormDto());
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("titulo", "Nuevo Expediente");
        model.addAttribute("subtitulo", "Registro de nuevo expediente");

        return "admin/expediente-form";
    }

    @PostMapping
    public String guardarExpediente(@ModelAttribute("expediente") Expediente expediente,
                                    @ModelAttribute("interesadosForm") InteresadosFormDto interesadosForm,
                                    @RequestParam Long clienteId,
                                    @RequestParam Long tipoTramiteId,
                                    Authentication authentication,
                                    Model model) {

        try {
            Expediente expedienteGuardado = expedienteService.crearExpedienteCompleto(
                    expediente,
                    clienteId,
                    tipoTramiteId,
                    interesadosForm.getInteresado1(),
                    interesadosForm.getInteresado2()
            );

            return "redirect:/expedientes/" + expedienteGuardado.getId();

        } catch (IllegalArgumentException e) {
            Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

            model.addAttribute("usuario", usuario);
            model.addAttribute("expediente", expediente);
            model.addAttribute("interesadosForm", interesadosForm);
            model.addAttribute("clientes", clienteService.listarTodos());
            model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
            model.addAttribute("clienteId", clienteId);
            model.addAttribute("tipoTramiteId", tipoTramiteId);
            model.addAttribute("titulo", "Nuevo Expediente");
            model.addAttribute("subtitulo", "Registro de nuevo expediente");
            model.addAttribute("error", e.getMessage());

            return "admin/expediente-form";
        }
    }
}


