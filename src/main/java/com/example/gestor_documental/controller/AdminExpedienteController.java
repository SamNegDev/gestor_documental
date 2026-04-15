package com.example.gestor_documental.controller;

import com.example.gestor_documental.dto.DocumentoFormDto;
import com.example.gestor_documental.dto.DocumentoFormWrapper;
import com.example.gestor_documental.dto.InteresadosFormDto;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.*;
import com.example.gestor_documental.validation.FormularioValidacionHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/expedientes")
public class AdminExpedienteController {

    private final ExpedienteService expedienteService;
    private final UsuarioService usuarioService;
    private final ClienteService clienteService;
    private final TipoTramiteService tipoTramiteService;
    private final DocumentoService documentoService;
    private final FormularioValidacionHelper formularioValidacionHelper;

    @GetMapping("/nuevo")
    public String formularioNuevoCliente(Authentication authentication, Model model) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("expediente", new Expediente());
        model.addAttribute("interesadosForm", new InteresadosFormDto());
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("documentosWrapper", new DocumentoFormWrapper());
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("titulo", "Nuevo Expediente");
        model.addAttribute("subtitulo", "Registro de nuevo expediente");

        return "admin/expediente-form";
    }

    @PostMapping
    public String guardarExpediente(@Valid @ModelAttribute("expediente") Expediente expediente,
            BindingResult expedienteResult,
            @Valid @ModelAttribute("interesadosForm") InteresadosFormDto interesadosForm,
            BindingResult interesadoResult,
            @ModelAttribute DocumentoFormWrapper documentosWrapper,
            @RequestParam Long clienteId,
            @RequestParam Long tipoTramiteId,
            Authentication authentication,
            Model model, RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        formularioValidacionHelper.validarDniOpcional(
                interesadoResult,
                "interesado1.dni",
                interesadosForm.getInteresado1() != null ? interesadosForm.getInteresado1().getDni() : null,
                "El DNI/NIE del interesado 1 no es válido");

        formularioValidacionHelper.validarDniOpcional(
                interesadoResult,
                "interesado2.dni",
                interesadosForm.getInteresado2() != null ? interesadosForm.getInteresado2().getDni() : null,
                "El DNI/NIE del interesado 2 no es válido");

        if (expedienteResult.hasErrors() || interesadoResult.hasErrors()) {
            cargarModeloFormularioExpediente(model, usuarioLogueado, expediente, interesadosForm,
                    documentosWrapper, clienteId, tipoTramiteId);
            return "admin/expediente-form";
        }

        try {
            Expediente expedienteGuardado = expedienteService.crearExpedienteCompleto(
                    expediente, usuarioLogueado,
                    clienteId,
                    tipoTramiteId,
                    interesadosForm.getInteresado1(),
                    interesadosForm.getInteresado2());

            if (documentosWrapper.getDocumentos() != null) {

                for (DocumentoFormDto doc : documentosWrapper.getDocumentos()) {

                    if (doc.getArchivo() != null && !doc.getArchivo().isEmpty()) {

                        documentoService.guardarParaExpediente(
                                expedienteGuardado.getId(),
                                doc.getArchivo(),
                                doc.getTipoDocumento(),
                                usuarioLogueado);
                    }
                }
            }
            redirectAttributes.addFlashAttribute("success", "Expediente creado correctamente");
            return "redirect:/expedientes/" + expedienteGuardado.getId();

        } catch (IllegalArgumentException e) {
            cargarModeloFormularioExpediente(model, usuarioLogueado, expediente, interesadosForm,
                    documentosWrapper, clienteId, tipoTramiteId);
            model.addAttribute("error", e.getMessage());

            return "admin/expediente-form";
        }
    }

    private void cargarModeloFormularioExpediente(Model model,
            Usuario usuarioLogueado,
            Expediente expediente,
            InteresadosFormDto interesadosForm,
            DocumentoFormWrapper documentosWrapper,
            Long clienteId,
            Long tipoTramiteId) {

        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("expediente", expediente);
        model.addAttribute("interesadosForm", interesadosForm);
        model.addAttribute("documentosWrapper",
                documentosWrapper != null ? documentosWrapper : new DocumentoFormWrapper());
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("clienteId", clienteId);
        model.addAttribute("tipoTramiteId", tipoTramiteId);
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("titulo", "Nuevo Expediente");
        model.addAttribute("subtitulo", "Registro de nuevo expediente");
    }

    @GetMapping("/{id}/editar")
    public String editarExpediente(@PathVariable Long id, Authentication authentication, Model model, RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        Expediente expediente = expedienteService.buscarPorId(id).orElse(null);

        if (expediente == null || !expedienteService.tienePermisoExpediente(expediente, usuarioLogueado)) {
            redirectAttributes.addFlashAttribute("error", "No tienes permiso para editar este expediente.");
            return "redirect:/expedientes";
        }

        if (expediente.getEstadoExpediente() == com.example.gestor_documental.enums.EstadoExpediente.FINALIZADO) {
            redirectAttributes.addFlashAttribute("error", "Vaya, los expedientes finalizados no se pueden editar.");
            return "redirect:/expedientes/" + id;
        }

        InteresadosFormDto interesadosForm = new InteresadosFormDto();
        if (expediente.getInteresados() != null) {
            if (expediente.getInteresados().size() > 0) {
                var rel1 = expediente.getInteresados().get(0);
                interesadosForm.getInteresado1().setRol(rel1.getRol());
                interesadosForm.getInteresado1().setNombre(rel1.getInteresado().getNombre());
                interesadosForm.getInteresado1().setDni(rel1.getInteresado().getDni());
                interesadosForm.getInteresado1().setTelefono(rel1.getInteresado().getTelefono());
                interesadosForm.getInteresado1().setDireccion(rel1.getInteresado().getDireccion());
            }
            if (expediente.getInteresados().size() > 1) {
                var rel2 = expediente.getInteresados().get(1);
                interesadosForm.getInteresado2().setRol(rel2.getRol());
                interesadosForm.getInteresado2().setNombre(rel2.getInteresado().getNombre());
                interesadosForm.getInteresado2().setDni(rel2.getInteresado().getDni());
                interesadosForm.getInteresado2().setTelefono(rel2.getInteresado().getTelefono());
                interesadosForm.getInteresado2().setDireccion(rel2.getInteresado().getDireccion());
            }
        }

        cargarModeloFormularioExpediente(model, usuarioLogueado, expediente, interesadosForm, null, expediente.getCliente().getId(), expediente.getTipoTramite().getId());
        model.addAttribute("titulo", "Editar Expediente");
        model.addAttribute("subtitulo", "Actualización de datos");

        return "admin/expediente-form";
    }

    @PostMapping("/{id}/editar")
    public String procesarEdicionExpediente(@PathVariable Long id,
                                            @Valid @ModelAttribute("expediente") Expediente expediente,
                                            BindingResult expedienteResult,
                                            @Valid @ModelAttribute("interesadosForm") InteresadosFormDto interesadosForm,
                                            BindingResult interesadoResult,
                                            @RequestParam Long clienteId,
                                            @RequestParam Long tipoTramiteId,
                                            Authentication authentication,
                                            Model model, RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        formularioValidacionHelper.validarDniOpcional(interesadoResult,"interesado1.dni",
                interesadosForm.getInteresado1() != null ? interesadosForm.getInteresado1().getDni() : null, "DNI/NIE no válido");
        formularioValidacionHelper.validarDniOpcional(interesadoResult,"interesado2.dni",
                interesadosForm.getInteresado2() != null ? interesadosForm.getInteresado2().getDni() : null, "DNI/NIE no válido");

        if (expedienteResult.hasErrors() || interesadoResult.hasErrors()) {
            cargarModeloFormularioExpediente(model, usuarioLogueado, expediente, interesadosForm, null, clienteId, tipoTramiteId);
            model.addAttribute("titulo", "Editar Expediente");
            return "admin/expediente-form";
        }

        try {
            expedienteService.actualizarExpediente(id, expediente, usuarioLogueado, clienteId, tipoTramiteId,
                    interesadosForm.getInteresado1(), interesadosForm.getInteresado2());
                    
            redirectAttributes.addFlashAttribute("success", "Expediente editado correctamente");
            return "redirect:/expedientes/" + id;
        } catch (IllegalArgumentException e) {
            cargarModeloFormularioExpediente(model, usuarioLogueado, expediente, interesadosForm, null, clienteId, tipoTramiteId);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("titulo", "Editar Expediente");
            return "admin/expediente-form";
        }
    }
}
