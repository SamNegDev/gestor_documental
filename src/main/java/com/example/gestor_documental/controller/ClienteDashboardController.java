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
@RequestMapping("/cliente/dashboard")
public class ClienteDashboardController {




        private final UsuarioService usuarioService;
        private final ExpedienteService expedienteService;

        @GetMapping
        public String dashboard(Authentication authentication, Model model) {

            Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

            int totalExpedientes = expedienteService.contarPorCliente(usuario.getCliente());
            int enTramite = expedienteService.contarPorClienteYEstado(usuario.getCliente(), EstadoExpediente.EN_TRAMITE);
            int finalizado = expedienteService.contarPorClienteYEstado(usuario.getCliente(), EstadoExpediente.FINALIZADO);
            int incidencia = expedienteService.contarPorClienteYEstado(usuario.getCliente(), EstadoExpediente.INCIDENCIA);

            model.addAttribute("usuario", usuario);
            model.addAttribute("titulo", "Dashboard");
            model.addAttribute("subtitulo", "Resumen general de tu actividad");


            model.addAttribute("totalExpedientes", totalExpedientes);
            model.addAttribute("enTramite", enTramite);
            model.addAttribute("finalizados", finalizado);
            model.addAttribute("incidencias", incidencia);

            return "cliente/dashboard";
        }

}
