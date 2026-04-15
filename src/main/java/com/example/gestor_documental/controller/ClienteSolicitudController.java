package com.example.gestor_documental.controller;

import com.example.gestor_documental.dto.DocumentoFormDto;
import com.example.gestor_documental.dto.DocumentoFormWrapper;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.SolicitudService;
import com.example.gestor_documental.service.TipoTramiteService;
import com.example.gestor_documental.service.UsuarioService;
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
@RequestMapping("/cliente/solicitudes")
public class ClienteSolicitudController {

    private final SolicitudService solicitudService;
    private final UsuarioService usuarioService;
    private final TipoTramiteService tipoTramiteService;
    private final DocumentoService documentoService;
    private final FormularioValidacionHelper formularioValidacionHelper;

    @GetMapping("/nuevo")
    public String nuevaSolicitud(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        if (usuarioLogueado.getCliente() == null) {
            redirectAttributes.addFlashAttribute("error",
                    "No tienes un cliente asociado. Contacta con administración.");
            return "redirect:/solicitudes";
        }

        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("solicitud", new Solicitud());
        model.addAttribute("cliente", usuarioLogueado.getCliente());
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("documentosWrapper", new DocumentoFormWrapper());
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("titulo", "Nueva Solicitud");
        model.addAttribute("subtitulo", "Registro de nueva solicitud");

        return "cliente/solicitud-form";
    }

    @PostMapping
    public String guardarSolicitud(@Valid @ModelAttribute("solicitud") Solicitud solicitud,
                                   BindingResult solicitudResult,
                                   @ModelAttribute DocumentoFormWrapper documentosWrapper,
                                   @RequestParam Long tipoTramiteId,
                                   Authentication authentication,
                                   Model model, RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        if (usuarioLogueado.getCliente() == null) {
            throw new AccesoDenegadoException("El usuarioLogueado no tiene cliente asociado");
        }

        Cliente cliente = usuarioLogueado.getCliente();

        formularioValidacionHelper.validarDniOpcional(
                solicitudResult,
                "interesado1Dni",
                solicitud.getInteresado1Dni(),
                "El DNI/NIE del interesado 1 no es válido"
        );

        formularioValidacionHelper.validarDniOpcional(
                solicitudResult,
                "interesado2Dni",
                solicitud.getInteresado2Dni(),
                "El DNI/NIE del interesado 2 no es válido"
        );

        if (solicitudResult.hasErrors()) {
            cargarModeloFormularioSolicitud(solicitud,
                    documentosWrapper,
                    tipoTramiteId,
                    model, usuarioLogueado,cliente);
            return "cliente/solicitud-form";
        }

        try {
            Solicitud solicitudGuardada = solicitudService.crearSolicitudCompleta(
                    solicitud, usuarioLogueado,
                    cliente,
                    tipoTramiteId
            );

            if (documentosWrapper.getDocumentos() != null) {
                for (DocumentoFormDto doc : documentosWrapper.getDocumentos()) {
                    if (doc.getArchivo() != null && !doc.getArchivo().isEmpty()) {
                        documentoService.guardarParaSolicitud(
                                solicitudGuardada.getId(),
                                doc.getArchivo(),
                                doc.getTipoDocumento(),
                                usuarioLogueado
                        );
                    }
                }
            }

            redirectAttributes.addFlashAttribute("success", "Solicitud creada correctamente");
            return "redirect:/solicitudes/" + solicitudGuardada.getId();

        } catch (IllegalArgumentException e) {
            cargarModeloFormularioSolicitud(solicitud,
                    documentosWrapper,
                    tipoTramiteId,
                    model, usuarioLogueado,cliente);

            model.addAttribute("error", e.getMessage());

            return "cliente/solicitud-form";
        }
    }
    private void cargarModeloFormularioSolicitud(Solicitud solicitud,
                                                 DocumentoFormWrapper documentosWrapper,
                                                 Long tipoTramiteId,
                                                 Model model, Usuario usuarioLogueado, Cliente cliente) {
        model.addAttribute("usuarioLogueado", usuarioLogueado);
        model.addAttribute("solicitud", solicitud);
        model.addAttribute("cliente", cliente);
        model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
        model.addAttribute("tipoTramiteId", tipoTramiteId);
        model.addAttribute("documentosWrapper", new DocumentoFormWrapper());
        model.addAttribute("tiposDocumento", TipoDocumento.values());
        model.addAttribute("titulo", "Nueva Solicitud");
        model.addAttribute("subtitulo", "Registro de nueva solicitud");

    }
}