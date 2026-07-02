package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.plantilla.DocumentoGeneradoResponse;
import com.example.gestor_documental.dto.plantilla.GenerarPlantillaRequest;
import com.example.gestor_documental.dto.plantilla.PlantillaPreviewRequest;
import com.example.gestor_documental.dto.plantilla.PlantillaPreviewResponse;
import com.example.gestor_documental.dto.plantilla.PlantillasExpedienteResponse;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.impl.PlantillaDocumentoService;
import com.example.gestor_documental.service.impl.PlantillaDocumentoService.PlantillaPdfFile;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solicitudes/{solicitudId}/plantillas")
@RequiredArgsConstructor
public class SolicitudPlantillaDocumentoApiController {

    private final PlantillaDocumentoService plantillaService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public PlantillasExpedienteResponse catalogo(@PathVariable Long solicitudId, Authentication authentication) {
        return plantillaService.catalogoSolicitud(solicitudId, usuario(authentication));
    }

    @PostMapping("/preview")
    public PlantillaPreviewResponse preview(@PathVariable Long solicitudId,
            @RequestBody PlantillaPreviewRequest request, Authentication authentication) {
        return plantillaService.previewSolicitud(solicitudId, request, usuario(authentication));
    }

    @PostMapping("/generar")
    public DocumentoGeneradoResponse generar(@PathVariable Long solicitudId,
            @RequestBody GenerarPlantillaRequest request, Authentication authentication) {
        return plantillaService.generarSolicitud(solicitudId, request, usuario(authentication));
    }

    @PostMapping(value = "/imprimir", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> imprimir(@PathVariable Long solicitudId,
            @RequestBody GenerarPlantillaRequest request, Authentication authentication) {
        PlantillaPdfFile pdf = plantillaService.generarPdfTemporalSolicitud(solicitudId, request, usuario(authentication));
        return pdfResponse(pdf);
    }

    private Usuario usuario(Authentication authentication) {
        return currentUserService.requireUser(authentication);
    }

    private ResponseEntity<byte[]> pdfResponse(PlantillaPdfFile pdf) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(pdf.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf.contenido());
    }
}
