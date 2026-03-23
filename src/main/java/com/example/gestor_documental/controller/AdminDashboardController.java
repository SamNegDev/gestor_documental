package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final UsuarioService usuarioService;
    private final ExpedienteService expedienteService;

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        long totalExpedientes = expedienteService.contarTodos();
        int enTramite = expedienteService.contarPorEstado(EstadoExpediente.EN_TRAMITE);
        int finalizado = expedienteService.contarPorEstado(EstadoExpediente.FINALIZADO);
        int incidencia = expedienteService.contarPorEstado(EstadoExpediente.INCIDENCIA);

        model.addAttribute("usuario", usuario);
        model.addAttribute("titulo", "Dashboard Admin");
        model.addAttribute("subtitulo", "Resumen general de la actividad de la gestoría");


        model.addAttribute("totalExpedientes", totalExpedientes);
        model.addAttribute("enTramite", enTramite);
        model.addAttribute("finalizados", finalizado);
        model.addAttribute("incidencias", incidencia);
        model.addAttribute("ultimosExpedientes", expedienteService.listarUltimos());

        return "admin/dashboard";
    }

}