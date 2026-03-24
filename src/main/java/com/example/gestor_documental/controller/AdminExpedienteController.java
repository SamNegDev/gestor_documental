package com.example.gestor_documental.controller;


import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/expedientes")
public class AdminExpedienteController {

    private final ExpedienteService expedienteService;
    private final UsuarioService usuarioService;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;

    @GetMapping("/nuevo")
    public String formularioNuevoCliente(Authentication authentication,Model model) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        model.addAttribute("usuario", usuario);
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("titulo", "Nuevo Expediente");
        model.addAttribute("subtitulo", "Registro de nuevo expediente");

        return "admin/expediente-form";
    }
    @PostMapping
    public String guardarExpediente(@RequestParam Long clienteId,
                                    @RequestParam Long tipoTramiteId,
                                    @RequestParam String matricula,
                                    @RequestParam(required = false) String observaciones) {
        Cliente cliente = clienteService.buscarPorId(clienteId).orElseThrow();
        TipoTramite tipoTramite = tipoTramiteService.buscarPorId(tipoTramiteId).orElseThrow();

        Expediente expediente = new Expediente();
        expediente.setCliente(cliente);
        expediente.setTipoTramite(tipoTramite);
        expediente.setMatricula(matricula);
        expediente.setObservaciones(observaciones);
        expediente.setEstadoExpediente(EstadoExpediente.EN_TRAMITE);

        expedienteService.guardar(expediente);

        return "redirect:/expedientes";

    }


}


