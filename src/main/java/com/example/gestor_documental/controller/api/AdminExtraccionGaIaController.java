package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.ia.ExtraccionGaLoteExportRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaJobRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaJobResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaPreviewResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaQueueItemResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaRevisionRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaRevisionResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaSincronizacionResponse;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ExtraccionGaIaService;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ia/extraccion-ga")
@RequiredArgsConstructor
public class AdminExtraccionGaIaController {

    private final ExtraccionGaIaService extraccionGaIaService;
    private final CurrentUserService currentUserService;

    @GetMapping("/expedientes/{expedienteId}/preview")
    public ExtraccionGaPreviewResponse preview(@PathVariable Long expedienteId, Authentication authentication) {
        return extraccionGaIaService.preview(expedienteId, currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/expedientes/{expedienteId}/probar")
    public ExtraccionGaResponse probar(
            @PathVariable Long expedienteId,
            @RequestBody(required = false) ExtraccionGaRequest request,
            Authentication authentication
    ) {
        return extraccionGaIaService.probar(expedienteId, request, currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/expedientes/{expedienteId}/probar-multiple")
    public ExtraccionGaResponse probarMultiple(
            @PathVariable Long expedienteId,
            @RequestBody(required = false) ExtraccionGaRequest request,
            Authentication authentication
    ) {
        return extraccionGaIaService.probarMultiple(expedienteId, request, currentUserService.requireAdmin(authentication));
    }

    @GetMapping("/expedientes/{expedienteId}/revision")
    public ExtraccionGaRevisionResponse obtenerRevision(@PathVariable Long expedienteId, Authentication authentication) {
        return extraccionGaIaService.obtenerRevision(expedienteId, currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/expedientes/{expedienteId}/revision")
    public ExtraccionGaRevisionResponse guardarRevision(
            @PathVariable Long expedienteId,
            @RequestBody ExtraccionGaRevisionRequest request,
            Authentication authentication
    ) {
        return extraccionGaIaService.guardarRevision(expedienteId, request, currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/expedientes/{expedienteId}/revision/preparar")
    public ExtraccionGaRevisionResponse prepararRevision(@PathVariable Long expedienteId, Authentication authentication) {
        return extraccionGaIaService.prepararRevision(expedienteId, currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/expedientes/{expedienteId}/revision/sincronizar")
    public ExtraccionGaSincronizacionResponse sincronizarRevision(@PathVariable Long expedienteId, Authentication authentication) {
        return extraccionGaIaService.sincronizarRevision(expedienteId, currentUserService.requireAdmin(authentication));
    }

    @GetMapping("/revisiones/preparadas")
    public List<ExtraccionGaRevisionResponse> listarPreparadas(Authentication authentication) {
        return extraccionGaIaService.listarPreparadas(currentUserService.requireAdmin(authentication));
    }

    @GetMapping("/revision/pendientes")
    public List<ExtraccionGaQueueItemResponse> listarPendientesRevision(Authentication authentication) {
        return extraccionGaIaService.listarPendientesRevision(currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/jobs")
    public List<ExtraccionGaJobResponse> crearJobs(
            @RequestBody ExtraccionGaJobRequest request,
            Authentication authentication
    ) {
        return extraccionGaIaService.crearJobs(request, currentUserService.requireAdmin(authentication));
    }

    @GetMapping("/jobs/activos")
    public List<ExtraccionGaJobResponse> listarJobsActivos(Authentication authentication) {
        return extraccionGaIaService.listarJobsActivos(currentUserService.requireAdmin(authentication));
    }

    @GetMapping("/jobs/{jobId}")
    public ExtraccionGaJobResponse obtenerJob(@PathVariable Long jobId, Authentication authentication) {
        return extraccionGaIaService.obtenerJob(jobId, currentUserService.requireAdmin(authentication));
    }

    @PostMapping("/revisiones/exportar")
    public ResponseEntity<byte[]> exportarPreparadas(
            @RequestBody ExtraccionGaLoteExportRequest request,
            Authentication authentication
    ) {
        byte[] xml = extraccionGaIaService.exportarPreparadas(
                request != null ? request.expedienteIds() : List.of(),
                currentUserService.requireAdmin(authentication)
        );
        String filename = "FORMATO_GA_LOTE_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".GA.XML";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }
}
