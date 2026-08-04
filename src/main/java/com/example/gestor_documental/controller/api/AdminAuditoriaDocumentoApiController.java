package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoResponse;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.AuditoriaDocumentoService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auditoria")
@RequiredArgsConstructor
public class AdminAuditoriaDocumentoApiController {

    private static final List<String> TIPOS_RECURSO = List.of(
            "DOCUMENTO", "EXPEDIENTE", "EXPORTACION_GA", "USUARIO", "ADMINISTRADOR_CLIENTE");

    private final AuditoriaDocumentoService auditoriaDocumentoService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public PagedResponse<AuditoriaDocumentoResponse> listar(
            @RequestParam(required = false) AccionAuditoriaDocumento accion,
            @RequestParam(required = false) ResultadoAuditoriaDocumento resultado,
            @RequestParam(required = false) String recursoTipo,
            @RequestParam(required = false) Long recursoId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long expedienteId,
            @RequestParam(required = false) Long documentoId,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanio,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        return auditoriaDocumentoService.listar(
                accion, resultado, recursoTipo, recursoId, clienteId, expedienteId, documentoId, usuarioId,
                desde, hasta, pagina, tamanio);
    }

    @GetMapping("/catalogos")
    public Map<String, List<String>> catalogos(Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return Map.of(
                "acciones", Arrays.stream(AccionAuditoriaDocumento.values()).map(Enum::name).toList(),
                "resultados", Arrays.stream(ResultadoAuditoriaDocumento.values()).map(Enum::name).toList(),
                "recursos", TIPOS_RECURSO);
    }
}
