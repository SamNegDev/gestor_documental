package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RequisitoDocumentalApiController {

    private final RequisitoDocumentalExpedienteService requisitoService;
    private final CurrentUserService currentUserService;

    @PostMapping("/expedientes/{expedienteId}/requisitos-documentales")
    public ResponseEntity<Void> crearRequisito(
            @PathVariable Long expedienteId,
            @RequestParam TipoDocumento tipoDocumento,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) Long interesadoId,
            @RequestParam(required = false) RolInteresado rolInteresado,
            @RequestParam(defaultValue = "REQUERIDO") EstadoRequisitoDocumental estadoInicial,
            Authentication authentication
    ) {
        requisitoService.crearManual(
                expedienteId,
                tipoDocumento,
                TextNormalizer.upperOrNull(descripcion),
                interesadoId,
                rolInteresado,
                estadoInicial,
                usuario(authentication)
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/requisitos-documentales/{id}/omitir")
    public ResponseEntity<Void> omitirRequisito(
            @PathVariable Long id,
            @RequestParam String motivo,
            Authentication authentication
    ) {
        requisitoService.omitir(id, TextNormalizer.upperOrNull(motivo), usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/requisitos-documentales/{id}/reabrir")
    public ResponseEntity<Void> reabrirRequisito(@PathVariable Long id, Authentication authentication) {
        requisitoService.reabrir(id, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/requisitos-documentales/{id}/vincular-documento")
    public ResponseEntity<Void> vincularDocumento(
            @PathVariable Long id,
            @RequestParam Long documentoId,
            Authentication authentication
    ) {
        requisitoService.vincularDocumento(id, documentoId, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requisitos-documentales/{id}/documento")
    public ResponseEntity<Void> subirDocumento(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication
    ) {
        requisitoService.subirDocumento(id, archivo, usuario(authentication));
        return ResponseEntity.noContent().build();
    }

    private Usuario usuario(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }
}
