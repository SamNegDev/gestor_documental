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

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        documentoService.guardarParaExpediente(expedienteId, archivo, tipoDocumento, usuarioLogueado);

        redirectAttributes.addFlashAttribute("mensaje", "Documento subido correctamente");
        return "redirect:/expedientes/" + expedienteId;
    }

    @PostMapping("/subir/solicitud")
    public String subirDocumentoSolicitud(@RequestParam("archivo") MultipartFile archivo,
                                          @RequestParam("solicitudId") Long solicitudId,
                                          @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        documentoService.guardarParaSolicitud(solicitudId, archivo, tipoDocumento, usuarioLogueado);

        redirectAttributes.addFlashAttribute("mensaje", "Documento subido correctamente");
        return "redirect:/solicitudes/" + solicitudId;
    }

    @GetMapping("/descargar/{id}")
    public void descargarDocumento(@PathVariable Long id,
                                   HttpServletResponse response,
                                   Authentication authentication) throws IOException {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);

        Path rutaBase = Paths.get("uploads").normalize().toAbsolutePath();
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

    @PostMapping("/eliminar/{id}")
    public String eliminarDocumento(@PathVariable Long id,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);

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

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);

        Path rutaBase = Paths.get("uploads").normalize().toAbsolutePath();
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

        if (nombre.endsWith(".pdf")) return "application/pdf";
        if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) return "image/jpeg";
        if (nombre.endsWith(".png")) return "image/png";
        if (nombre.endsWith(".gif")) return "image/gif";
        if (nombre.endsWith(".txt")) return "text/plain";

        return "application/octet-stream";
    }

    @PostMapping("/editar/{id}")
    public String editarDocumento(@PathVariable Long id,
                                  @RequestParam(required = false) TipoDocumento nuevoTipoDocumento,
                                  @RequestParam(required = false) String nuevoNombreArchivo,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        documentoService.actualizarDocumento(id, nuevoTipoDocumento, nuevoNombreArchivo, usuarioLogueado);

        Documento documento = documentoService.buscarPorId(id).orElseThrow();
        Long entidadId = documento.getExpediente() != null ? documento.getExpediente().getId() : documento.getSolicitud().getId();

        redirectAttributes.addFlashAttribute("mensaje", "Documento actualizado correctamente");
        if (documento.getExpediente() != null) {
            return "redirect:/expedientes/" + entidadId;
        }
        return "redirect:/solicitudes/" + entidadId;
    }

    @PostMapping("/extraer/{id}")
    public String extraerPaginas(@PathVariable Long id,
                                 @RequestParam String rangoPaginas,
                                 @RequestParam TipoDocumento nuevoTipoDocumento,
                                 @RequestParam String nuevoNombreArchivo,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {

        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());

        documentoService.extraerPaginasDocumento(id, rangoPaginas, nuevoTipoDocumento, nuevoNombreArchivo, usuarioLogueado);

        Documento documento = documentoService.buscarPorId(id).orElseThrow();
        Long entidadId = documento.getExpediente() != null ? documento.getExpediente().getId() : documento.getSolicitud().getId();

        redirectAttributes.addFlashAttribute("mensaje", "Páginas extraídas y guardadas como nuevo documento correctamente");
        if (documento.getExpediente() != null) {
            return "redirect:/expedientes/" + entidadId;
        }
        return "redirect:/solicitudes/" + entidadId;
    }
}