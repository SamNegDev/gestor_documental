package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentoApiController {

    private final DocumentoService documentoService;
    private final UsuarioService usuarioService;

    @PostMapping("/expedientes/{expedienteId}/documentos")
    public ResponseEntity<Void> subirDocumento(
            @PathVariable Long expedienteId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        documentoService.guardarParaExpediente(expedienteId, archivo, tipoDocumento, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/documentos/{id}")
    public ResponseEntity<Void> editarDocumento(
            @PathVariable Long id,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) String nombreArchivo,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        documentoService.actualizarDocumento(id, tipoDocumento, nombreArchivo, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documentos/{id}")
    public ResponseEntity<Void> eliminarDocumento(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/documentos/{id}/paginas")
    public ResponseEntity<Void> eliminarPaginas(
            @PathVariable Long id,
            @RequestParam String rangoPaginas,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        documentoService.eliminarPaginasDocumento(id, rangoPaginas, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documentos/{id}/extraer")
    public ResponseEntity<Void> extraerPaginas(
            @PathVariable Long id,
            @RequestParam String rangoPaginas,
            @RequestParam TipoDocumento tipoDocumento,
            @RequestParam String nombreArchivo,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        documentoService.extraerPaginasDocumento(id, rangoPaginas, tipoDocumento, nombreArchivo, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/documentos/{id}/paginas")
    public ResponseEntity<Map<String, Integer>> contarPaginas(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        return ResponseEntity.ok(Map.of("totalPaginas", documentoService.contarPaginasDocumento(id, usuarioLogueado)));
    }

    @org.springframework.web.bind.annotation.GetMapping(value = "/documentos/{id}/paginas/{pagina}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previsualizarPagina(
            @PathVariable Long id,
            @PathVariable int pagina,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(documentoService.renderizarPaginaDocumento(id, pagina, usuarioLogueado));
    }

    @PostMapping("/documentos/{id}/unir")
    public ResponseEntity<Void> unirDocumentos(
            @PathVariable Long id,
            @RequestParam String documentoIds,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) String nombreArchivo,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = usuarioService.buscarPorEmail(authentication.getName());
        List<Long> ids = Arrays.stream(documentoIds.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isBlank())
                .map(Long::valueOf)
                .toList();
        documentoService.unirDocumentos(id, ids, tipoDocumento, nombreArchivo, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }
}
