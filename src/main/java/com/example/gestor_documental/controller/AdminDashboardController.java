package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.SolicitudService;
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
    private final SolicitudService solicitudService;

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        long totalExpedientes = expedienteService.contarTodos();
        int enTramite = expedienteService.contarPorEstado(EstadoExpediente.EN_TRAMITE);
        int finalizado = expedienteService.contarPorEstado(EstadoExpediente.FINALIZADO);
        int incidencia = expedienteService.contarPorEstado(EstadoExpediente.INCIDENCIA);

        long totalSolicitudes = solicitudService.contarTodos();
        long pendienteRevision = solicitudService.contarPorEstado(EstadoSolicitud.PENDIENTE_REVISION);
        long convertidas = solicitudService.contarPorEstado(EstadoSolicitud.CONVERTIDO);
        long incidenciasSoliticudes = solicitudService.contarPorEstado(EstadoSolicitud.INCIDENCIA);

        model.addAttribute("usuario", usuario);
        model.addAttribute("titulo", "Dashboard Admin");
        model.addAttribute("subtitulo", "Resumen general de la actividad de la gestoría");

        model.addAttribute("totalExpedientes", totalExpedientes);
        model.addAttribute("enTramite", enTramite);
        model.addAttribute("finalizados", finalizado);
        model.addAttribute("incidencias", incidencia);
        model.addAttribute("ultimosExpedientes", expedienteService.listarUltimos());

        model.addAttribute("totalSolicitudes", totalSolicitudes);
        model.addAttribute("pendienteRevision", pendienteRevision);
        model.addAttribute("convertidas", convertidas);
        model.addAttribute("incidenciasSoliticudes", incidenciasSoliticudes);
        model.addAttribute("ultimasSolicitudes", solicitudService.listarUltimas());




        return "admin/dashboard";
    }

}