package com.example.gestor_documental.controller;

import com.example.gestor_documental.service.DocumentoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }
    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "ok";
    }

    @PostMapping("/subir")
    public String subirDocumento(@RequestParam("archivo") MultipartFile archivo,
                                 @RequestParam("expedienteId") Long expedienteId) {

        System.out.println("ENTRA EN SUBIR DOCUMENTO");
        documentoService.guardar(expedienteId, archivo);


        return "redirect:/expedientes/" + expedienteId;
    }
}