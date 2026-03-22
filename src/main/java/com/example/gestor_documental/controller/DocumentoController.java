package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RequiredArgsConstructor
@Controller
@RequestMapping("/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final ExpedienteService expedienteService;
    private final UsuarioService usuarioService;


    @PostMapping("/subir")
    public String subirDocumento(@RequestParam("archivo") MultipartFile archivo,
                                 @RequestParam("expedienteId") Long expedienteId,
                                 @RequestParam("tipoDocumento")TipoDocumento tipoDocumento,
                                 RedirectAttributes redirectAttributes) {

        documentoService.guardar(expedienteId, archivo, tipoDocumento);

        redirectAttributes.addFlashAttribute("mensaje", "Documento subido correctamente");


        return "redirect:/expedientes/" + expedienteId;
    }

    @GetMapping("/descargar/{id}")
    public void descargarDocumento(@PathVariable Long id,
                                   HttpServletResponse response,
                                   Authentication authentication) throws IOException {

        Documento documento = documentoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        Path rutaArchivo = Paths.get("uploads").resolve(documento.getNombreArchivo());

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        if (!expedienteService.tienePermisoExpediente(documento.getExpediente(), usuario)) {
            response.sendRedirect("/expedientes");
            return;
        }

        if (!Files.exists(rutaArchivo)) {
            throw new RuntimeException("El archivo no existe en disco");
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + documento.getNombreArchivoOriginal() + "\"");

        Files.copy(rutaArchivo, response.getOutputStream());
        response.getOutputStream().flush();

    }
    @PostMapping("/eliminar/{id}")
    public String eliminarDocumento(@PathVariable Long id,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {

        Documento documento = documentoService.buscarPorId(id)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        if (!expedienteService.tienePermisoExpediente(documento.getExpediente(), usuario)) {
            redirectAttributes.addFlashAttribute("mensaje", "No tienes permiso para eliminar este documento");
            return "redirect:/expedientes";
        }

        Long expedienteId = documentoService.eliminar(id);

        redirectAttributes.addFlashAttribute("mensaje", "Documento eliminado correctamente");

        return "redirect:/expedientes/" + expedienteId;
    }
    private String obtenerContentType(String nombreArchivo) {

        if (nombreArchivo.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (nombreArchivo.endsWith(".png")) {
            return "image/png";
        }
        if (nombreArchivo.endsWith(".jpg") || nombreArchivo.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "application/octet-stream";
    }
    @GetMapping("/ver/{id}")
    public void verDocumento(@PathVariable Long id,
                             Authentication authentication,
                             HttpServletResponse response) throws IOException {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuario);

        Path rutaArchivo = Paths.get("uploads").resolve(documento.getNombreArchivo());

        if (!Files.exists(rutaArchivo)) {
            throw new RuntimeException("El archivo no existe");
        }

        response.setContentType(obtenerContentType(documento.getNombreArchivoOriginal()));
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + documento.getNombreArchivoOriginal() + "\"");

        Files.copy(rutaArchivo, response.getOutputStream());
        response.getOutputStream().flush();
    }

}