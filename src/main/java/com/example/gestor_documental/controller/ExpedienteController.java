package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.service.ClienteService;
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
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.TipoIncidenciaService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.MensajeService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/expedientes")
public class ExpedienteController {

    private final ExpedienteService expedienteService;
    private final UsuarioService usuarioService;
    private final DocumentoService documentoService;
    private final ClienteService clienteService;
    private final TipoIncidenciaService tipoIncidenciaService;
    private final IncidenciaService incidenciaService;
    private final HistorialCambioService historialCambioService;
    private final TipoTramiteService tipoTramiteService;
    private final MensajeService mensajeService;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;

    @GetMapping
    public String listarExpedientes(Authentication authentication, Model model,
            @RequestParam(required = false) EstadoExpediente estado,
            @RequestParam(required = false) Long tipoTramiteId,
            @RequestParam(required = false) String matricula,
            @RequestParam(required = false) Long clienteId) {

        String email = authentication.getName();

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);

        List<Expediente> expedientes;

        if (usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            expedientes = expedienteService.listarTodos();
            model.addAttribute("clientes", clienteService.listarTodos());
            if (clienteId != null) {
                expedientes = expedientes.stream()
                        .filter(e -> e.getCliente() != null && e.getCliente().getId().equals(clienteId))
                        .toList();
                model.addAttribute("clienteSeleccionado", clienteId);
            }
        } else {

            if (usuarioLogueado.getCliente() == null) {
                expedientes = List.of();
            } else {
                expedientes = expedienteService.listarPorClienteId(usuarioLogueado.getCliente().getId());
            }

        }
        if (estado != null) {
            expedientes = expedientes.stream()
                    .filter(e -> e.getEstadoExpediente() == estado)
                    .toList();
        }
        if (tipoTramiteId != null) {
            expedientes = expedientes.stream()
                    .filter(e -> e.getTipoTramite() != null && e.getTipoTramite().getId().equals(tipoTramiteId))
                    .toList();
        }
        if (matricula != null) {
            String matriculaBusqueda = matricula.trim();

            if (!matriculaBusqueda.isEmpty()) {
                expedientes = expedientes.stream()
                        .filter(e -> e.getMatricula() != null &&
                                e.getMatricula().toLowerCase().contains(matriculaBusqueda.toLowerCase()))
                        .toList();
            }
        }
        boolean hayFiltrosActivos = estado != null
                || tipoTramiteId != null || clienteId != null
                || (matricula != null && !matricula.trim().isEmpty());

        model.addAttribute("hayFiltrosActivos", hayFiltrosActivos);
        model.addAttribute("expedientes", expedientes);
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("estadosExpediente", EstadoExpediente.values());
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("tipoTramiteIdSeleccionado", tipoTramiteId);
        model.addAttribute("matriculaSeleccionada", matricula);

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
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        String email = authentication.getName();

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(email);


        if (!expedienteService.tienePermisoExpediente(expediente, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }

        List<Documento> documentos = documentoService.listarPorExpediente(id);
        List<Incidencia> incidencias = incidenciaService.listarPorExpediente(id);
        List<ExpedienteInteresado> interesados = expedienteInteresadoRepository.findByExpedienteId(id);
        
        model.addAttribute("historialCambios", historialCambioService.listarPorExpediente(id));

        model.addAttribute("expediente", expediente);
        model.addAttribute("documentos", documentos);
        model.addAttribute("incidencias", incidencias);
        model.addAttribute("interesados", interesados);
        model.addAttribute("mensajes", mensajeService.listarPorExpediente(id));
        model.addAttribute("tiposIncidencia", tipoIncidenciaService.listarTodosActivos());
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("titulo", "Detalle Expediente");
        model.addAttribute("subtitulo", "Datos del expediente y documentos asociados");
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

    @PostMapping("/{id}/mensajes")
    public String añadirMensajeExpediente(@PathVariable Long id,
            @RequestParam String contenido,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        try {
            mensajeService.añadirAExpediente(id, contenido, usuarioLogueado);
            redirectAttributes.addFlashAttribute("success", "Mensaje enviado correctamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/expedientes/" + id;
    }



}
