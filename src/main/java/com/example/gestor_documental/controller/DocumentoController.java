package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.service.DocumentoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;



import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
                                 @RequestParam("expedienteId") Long expedienteId,@RequestParam("tipoDocumento")
    TipoDocumento tipoDocumento) {

        System.out.println("ENTRA CONTROLLER");
        documentoService.guardar(expedienteId, archivo, tipoDocumento);
        System.out.println("SALE CONTROLLER");

        return "redirect:/expedientes/" + expedienteId;
    }

    @GetMapping("/descargar/{id}")
    public void descargarDocumento(@PathVariable Long id, HttpServletResponse response) throws IOException {

        System.out.println("1. Entra en descargarDocumento. ID = " + id);

        Documento documento = documentoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        System.out.println("2. Documento encontrado: " + documento.getNombreArchivoOriginal());
        System.out.println("3. Nombre archivo guardado en disco: " + documento.getNombreArchivo());

        Path rutaArchivo = Paths.get("uploads").resolve(documento.getNombreArchivo());

        System.out.println("4. Ruta completa: " + rutaArchivo.toAbsolutePath());

        if (!Files.exists(rutaArchivo)) {
            throw new RuntimeException("El archivo no existe en disco");
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + documento.getNombreArchivoOriginal() + "\"");

        Files.copy(rutaArchivo, response.getOutputStream());
        response.getOutputStream().flush();

        System.out.println("5. Archivo enviado correctamente");
    }
}