package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.enums.CategoriaHistorial;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialExpedienteExportService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
public class HistorialExpedienteExportApiController {

    private static final MediaType CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final HistorialExpedienteExportService exportService;
    private final ExpedienteService expedienteService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/expedientes/{id}/historial/exportar")
    public ResponseEntity<StreamingResponseBody> exportarAdmin(
            @PathVariable Long id,
            @RequestParam(required = false) CategoriaHistorial categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        validarAccesoAdmin(id, authentication);
        return respuesta(id, categoria, desde, hasta, false);
    }

    @GetMapping("/api/cliente/expedientes/{id}/historial/exportar")
    public ResponseEntity<StreamingResponseBody> exportarCliente(
            @PathVariable Long id,
            @RequestParam(required = false) CategoriaHistorial categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        validarAcceso(id, authentication);
        return respuesta(id, categoria, desde, hasta, true);
    }

    private void validarAccesoAdmin(Long expedienteId, Authentication authentication) {
        Usuario usuario = validarAcceso(expedienteId, authentication);
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede exportar el historial interno");
        }
    }
    private Usuario validarAcceso(Long expedienteId, Authentication authentication) {
        Usuario usuario = currentUserService.requireUser(authentication);
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }
        return usuario;
    }

    private ResponseEntity<StreamingResponseBody> respuesta(
            Long expedienteId,
            CategoriaHistorial categoria,
            LocalDate desde,
            LocalDate hasta,
            boolean soloCliente
    ) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La fecha hasta no puede ser anterior a la fecha desde");
        }
        StreamingResponseBody body = outputStream ->
                exportService.exportarCsv(expedienteId, categoria, desde, hasta, soloCliente, outputStream);
        String filename = "historial-expediente-" + expedienteId + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
