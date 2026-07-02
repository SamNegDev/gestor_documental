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
@RequestMapping("/api/expedientes/{expedienteId}/plantillas")
@RequiredArgsConstructor
public class PlantillaDocumentoApiController {

    private final PlantillaDocumentoService plantillaService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public PlantillasExpedienteResponse catalogo(@PathVariable Long expedienteId, Authentication authentication) {
        return plantillaService.catalogo(expedienteId, requireAdmin(authentication));
    }

    @PostMapping("/preview")
    public PlantillaPreviewResponse preview(@PathVariable Long expedienteId,
            @RequestBody PlantillaPreviewRequest request, Authentication authentication) {
        return plantillaService.preview(expedienteId, request, requireAdmin(authentication));
    }

    @PostMapping("/generar")
    public DocumentoGeneradoResponse generar(@PathVariable Long expedienteId,
            @RequestBody GenerarPlantillaRequest request, Authentication authentication) {
        return plantillaService.generar(expedienteId, request, requireAdmin(authentication));
    }

    @PostMapping(value = "/imprimir", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> imprimir(@PathVariable Long expedienteId,
            @RequestBody GenerarPlantillaRequest request, Authentication authentication) {
        PlantillaPdfFile pdf = plantillaService.generarPdfTemporal(expedienteId, request, requireAdmin(authentication));
        return pdfResponse(pdf);
    }

    private Usuario requireAdmin(Authentication authentication) {
        return currentUserService.requireAdmin(authentication);
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
