package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/incidencias")
@RequiredArgsConstructor
public class IncidenciaApiController {

    private final IncidenciaService incidenciaService;
    private final DocumentoService documentoService;
    private final UsuarioService usuarioService;

    @PostMapping("/{id}/resolver")
    public ResponseEntity<Void> resolver(@PathVariable Long id, Authentication authentication) {
        incidenciaService.resolverIncidencia(id, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reclamar")
    public ResponseEntity<Void> reclamar(
            @PathVariable Long id,
            @RequestParam(required = false) String observaciones,
            Authentication authentication
    ) {
        incidenciaService.reclamarIncidencia(id, observaciones, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documento")
    public ResponseEntity<Void> subirDocumento(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "DOCUMENTO_INCIDENCIA") TipoDocumento tipoDocumento,
            Authentication authentication
    ) {
        documentoService.guardarParaIncidencia(id, archivo, tipoDocumento, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/documentos/{documentoId}/vincular")
    public ResponseEntity<Void> vincularDocumento(
            @PathVariable Long id,
            @PathVariable Long documentoId,
            Authentication authentication
    ) {
        documentoService.vincularAIncidencia(id, documentoId, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    private Usuario usuario(Authentication authentication) {
        return usuarioService.buscarPorEmail(authentication.getName());
    }
}
