package com.example.gestor_documental.controller;

import com.example.gestor_documental.dto.DocumentoFormDto;
import com.example.gestor_documental.dto.DocumentoFormWrapper;
import com.example.gestor_documental.dto.InteresadosFormDto;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.*;
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
    private final DocumentoService documentoService;


    @GetMapping("/nuevo")
    public String formularioNuevoCliente(Authentication authentication, Model model) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        System.out.println("ENTRA AL GET");

        model.addAttribute("usuario", usuario);
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
    public String guardarExpediente(@ModelAttribute("expediente") Expediente expediente,
                                    @ModelAttribute("interesadosForm") InteresadosFormDto interesadosForm,
                                    @ModelAttribute DocumentoFormWrapper documentosWrapper,
                                    @RequestParam Long clienteId,
                                    @RequestParam Long tipoTramiteId,
                                    Authentication authentication,
                                    Model model) {


        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        try {
            Expediente expedienteGuardado = expedienteService.crearExpedienteCompleto(
                    expediente,
                    clienteId,
                    tipoTramiteId,
                    interesadosForm.getInteresado1(),
                    interesadosForm.getInteresado2()
            );

            if (documentosWrapper.getDocumentos() != null) {

                for (DocumentoFormDto doc : documentosWrapper.getDocumentos()) {

                    if (doc.getArchivo() != null && !doc.getArchivo().isEmpty()) {

                        documentoService.guardarParaExpediente(
                                expedienteGuardado.getId(),
                                doc.getArchivo(),
                                doc.getTipoDocumento(),
                                usuario
                        );
                    }
                }
            }

            return "redirect:/expedientes/" + expedienteGuardado.getId();

        } catch (IllegalArgumentException e) {



            model.addAttribute("usuario", usuario);
            model.addAttribute("expediente", expediente);
            model.addAttribute("interesadosForm", interesadosForm);
            model.addAttribute("clientes", clienteService.listarTodos());
            model.addAttribute("tiposTramite", tipoTramiteService.listarTodos());
            model.addAttribute("clienteId", clienteId);
            model.addAttribute("tipoTramiteId", tipoTramiteId);
            model.addAttribute("tiposDocumento", TipoDocumento.values());
            model.addAttribute("documentosWrapper", new DocumentoFormWrapper());
            model.addAttribute("titulo", "Nuevo Expediente");
            model.addAttribute("subtitulo", "Registro de nuevo expediente");
            model.addAttribute("error", e.getMessage());

            return "admin/expediente-form";
        }
    }
}


