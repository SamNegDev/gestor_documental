package com.example.gestor_documental.controller;

import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RequiredArgsConstructor
@Controller
@RequestMapping("/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final UsuarioService usuarioService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping("/descargar/{id}")
    public void descargarDocumento(@PathVariable Long id,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);

        Path rutaBase = Paths.get(uploadDir).normalize().toAbsolutePath();
        Path rutaArchivo = rutaBase.resolve(documento.getNombreArchivo()).normalize();

        if (!rutaArchivo.startsWith(rutaBase)) {
            throw new RuntimeException("Acceso denegado: Ruta de archivo no permitida");
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

    @GetMapping("/ver/{id}")
    public void verDocumento(@PathVariable Long id,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);

        Path rutaBase = Paths.get(uploadDir).normalize().toAbsolutePath();
        Path rutaArchivo = rutaBase.resolve(documento.getNombreArchivo()).normalize();

        if (!rutaArchivo.startsWith(rutaBase)) {
            throw new RuntimeException("Acceso denegado: Ruta de archivo no permitida");
        }

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

        if (nombre.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (nombre.endsWith(".png")) {
            return "image/png";
        }
        if (nombre.endsWith(".gif")) {
            return "image/gif";
        }
        if (nombre.endsWith(".txt")) {
            return "text/plain";
        }

        return "application/octet-stream";
    }
}
