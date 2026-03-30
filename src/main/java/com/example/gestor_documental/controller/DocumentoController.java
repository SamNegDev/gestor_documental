package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
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
    private final UsuarioService usuarioService;

    @PostMapping("/subir/expediente")
    public String subirDocumentoExpediente(@RequestParam("archivo") MultipartFile archivo,
                                           @RequestParam("expedienteId") Long expedienteId,
                                           @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        documentoService.guardarParaExpediente(expedienteId, archivo, tipoDocumento, usuario);

        redirectAttributes.addFlashAttribute("mensaje", "Documento subido correctamente");
        return "redirect:/expedientes/" + expedienteId;
    }

    @PostMapping("/subir/solicitud")
    public String subirDocumentoSolicitud(@RequestParam("archivo") MultipartFile archivo,
                                          @RequestParam("solicitudId") Long solicitudId,
                                          @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes) {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());

        documentoService.guardarParaSolicitud(solicitudId, archivo, tipoDocumento, usuario);

        redirectAttributes.addFlashAttribute("mensaje", "Documento subido correctamente");
        return "redirect:/solicitudes/" + solicitudId;
    }

    @GetMapping("/descargar/{id}")
    public void descargarDocumento(@PathVariable Long id,
                                   HttpServletResponse response,
                                   Authentication authentication) throws IOException {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuario);

        Path rutaArchivo = Paths.get("uploads").resolve(documento.getNombreArchivo());

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

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuario);

        Long entidadId = documentoService.eliminar(id);

        redirectAttributes.addFlashAttribute("mensaje", "Documento eliminado correctamente");

        if (documento.getExpediente() != null) {
            return "redirect:/expedientes/" + entidadId;
        }

        return "redirect:/solicitudes/" + entidadId;
    }

    @GetMapping("/ver/{id}")
    public void verDocumento(@PathVariable Long id,
                             HttpServletResponse response,
                             Authentication authentication) throws IOException {

        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuario);

        Path rutaArchivo = Paths.get("uploads").resolve(documento.getNombreArchivo());

        if (!Files.exists(rutaArchivo)) {
            throw new RuntimeException("El archivo no existe en disco");
        }

        response.setContentType(obtenerContentType(documento.getNombreArchivoOriginal()));
        response.setHeader("Content-Disposition",
                "inline; filename=\"" + documento.getNombreArchivoOriginal() + "\"");

        Files.copy(rutaArchivo, response.getOutputStream());
        response.getOutputStream().flush();
    }

    private String obtenerContentType(String nombreArchivo) {
        String nombre = nombreArchivo.toLowerCase();

        if (nombre.endsWith(".pdf")) return "application/pdf";
        if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) return "image/jpeg";
        if (nombre.endsWith(".png")) return "image/png";
        if (nombre.endsWith(".gif")) return "image/gif";
        if (nombre.endsWith(".txt")) return "text/plain";

        return "application/octet-stream";
    }
}