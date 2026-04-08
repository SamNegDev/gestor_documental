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
@RequestMapping("/cliente/dashboard")
public class ClienteDashboardController {

        private final UsuarioService usuarioService;
        private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;

    @GetMapping
        public String dashboard(Authentication authentication, Model model) {

            Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

            int totalExpedientes = expedienteService.contarPorCliente(usuarioLogueado.getCliente());
            int enTramite = expedienteService.contarPorClienteYEstado(usuarioLogueado.getCliente(), EstadoExpediente.EN_TRAMITE);
            int finalizado = expedienteService.contarPorClienteYEstado(usuarioLogueado.getCliente(), EstadoExpediente.FINALIZADO);
            int incidenciasExpedientes = expedienteService.contarPorClienteYEstado(usuarioLogueado.getCliente(), EstadoExpediente.INCIDENCIA);

            int totalSolicitudes = solicitudService.contarPorCliente(usuarioLogueado.getCliente());
            int pendienteRevision = solicitudService.contarPorClienteYEstado(usuarioLogueado.getCliente(), EstadoSolicitud.PENDIENTE_REVISION);
            int convertidas = solicitudService.contarPorClienteYEstado(usuarioLogueado.getCliente(), EstadoSolicitud.CONVERTIDO);
            int incidenciasSolicitudes = solicitudService.contarPorClienteYEstado(usuarioLogueado.getCliente(), EstadoSolicitud.INCIDENCIA);

            long totalIncidencias = incidenciasExpedientes + incidenciasSolicitudes;


            model.addAttribute("usuarioLogueado", usuarioLogueado);
            model.addAttribute("titulo", "Dashboard");
            model.addAttribute("subtitulo", "Resumen general de tu actividad");

            model.addAttribute("totalSolicitudes", totalSolicitudes);
            model.addAttribute("pendienteRevision", pendienteRevision);
            model.addAttribute("convertidas", convertidas);
            model.addAttribute("incidenciasSoliticudes", incidenciasSolicitudes);
            model.addAttribute("ultimasSolicitudes", solicitudService.listarUltimasPorCliente(usuarioLogueado.getCliente()));


            model.addAttribute("totalExpedientes", totalExpedientes);
            model.addAttribute("enTramite", enTramite);
            model.addAttribute("finalizados", finalizado);
            model.addAttribute("incidencias", incidenciasExpedientes);
            model.addAttribute("ultimosExpedientes", expedienteService.listarUltimosPorCliente(usuarioLogueado.getCliente()));

            model.addAttribute("totalIncidencias", totalIncidencias);


            return "cliente/dashboard";
        }

}
