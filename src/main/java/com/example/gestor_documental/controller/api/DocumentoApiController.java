package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse;
import com.example.gestor_documental.dto.expediente.ProcesamientoExpedienteCompletoResponse;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import com.example.gestor_documental.service.ExpedienteCompletoProcesamientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final DocumentoIdentidadLecturaService documentoIdentidadLecturaService;
    private final DocumentoRolesLecturaService documentoRolesLecturaService;
    private final DocumentoVehiculoLecturaService documentoVehiculoLecturaService;
    private final ExpedienteCompletoProcesamientoService expedienteCompletoProcesamientoService;
    private final CurrentUserService currentUserService;

    @PostMapping("/expedientes/{expedienteId}/documentos")
    public ResponseEntity<Void> subirDocumento(
            @PathVariable Long expedienteId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("tipoDocumento") TipoDocumento tipoDocumento,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        documentoService.guardarParaExpediente(expedienteId, archivo, tipoDocumento, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/expedientes/{expedienteId}/documentos/expediente-completo/procesamientos")
    public ProcesamientoExpedienteCompletoResponse iniciarProcesamientoExpedienteCompleto(
            @PathVariable Long expedienteId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return expedienteCompletoProcesamientoService.iniciar(expedienteId, archivo, operacionId, usuarioLogueado);
    }

    @GetMapping("/procesamientos-expediente-completo/{jobId}")
    public ProcesamientoExpedienteCompletoResponse obtenerProcesamientoExpedienteCompleto(
            @PathVariable String jobId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return expedienteCompletoProcesamientoService.obtener(jobId, usuarioLogueado);
    }

    @PostMapping("/documentos/{id}/expediente-completo/procesamientos")
    public ProcesamientoExpedienteCompletoResponse reencolarProcesamientoExpedienteCompleto(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return expedienteCompletoProcesamientoService.iniciarDocumentoExistente(id, usuarioLogueado);
    }

    @PatchMapping("/documentos/{id}")
    public ResponseEntity<Void> editarDocumento(
            @PathVariable Long id,
            @RequestParam(required = false) TipoDocumento tipoDocumento,
            @RequestParam(required = false) String nombreArchivo,
            @RequestParam(required = false) Long operacionId,
            @RequestParam(required = false, defaultValue = "false") boolean actualizarOperacion,
            @RequestParam(required = false, defaultValue = "false") boolean nombreAutomatico,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        documentoService.actualizarDocumento(id, tipoDocumento, nombreArchivo, operacionId, actualizarOperacion, nombreAutomatico, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documentos/{id}")
    public ResponseEntity<Void> eliminarDocumento(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        Documento documento = documentoService.obtenerDocumentoConPermiso(id, usuarioLogueado);
        boolean documentoClienteMaestro = documento.getCliente() != null && documento.getInteresado() == null;
        if (documentoClienteMaestro && usuarioLogueado.getRolUsuario() != RolUsuario.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Solo un administrador puede eliminar documentos del cliente"
            );
        }
        documentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documentos/{id}/lectura-identidad")
    public ResponseEntity<DocumentoIdentidadLecturaResponse> obtenerLecturaIdentidad(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        DocumentoIdentidadLecturaResponse lectura = documentoIdentidadLecturaService.obtenerLectura(id, usuarioLogueado);
        if (lectura == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lectura);
    }

    @PostMapping("/documentos/{id}/lectura-identidad")
    public ResponseEntity<DocumentoIdentidadLecturaResponse> leerIdentidad(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean forzar,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(documentoIdentidadLecturaService.leerIdentidad(id, forzar, usuarioLogueado));
    }

    @GetMapping("/documentos/{id}/lectura-roles")
    public ResponseEntity<DocumentoRolesLecturaResponse> obtenerLecturaRoles(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        DocumentoRolesLecturaResponse lectura = documentoRolesLecturaService.obtenerLectura(id, usuarioLogueado);
        if (lectura == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lectura);
    }

    @PostMapping("/documentos/{id}/lectura-roles")
    public ResponseEntity<DocumentoRolesLecturaResponse> leerRoles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean forzar,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(documentoRolesLecturaService.leerRoles(id, forzar, usuarioLogueado));
    }

    @GetMapping("/documentos/{id}/lectura-vehiculo")
    public ResponseEntity<DocumentoVehiculoLecturaResponse> obtenerLecturaVehiculo(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        DocumentoVehiculoLecturaResponse lectura = documentoVehiculoLecturaService.obtenerLectura(id, usuarioLogueado);
        if (lectura == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lectura);
    }

    @PostMapping("/documentos/{id}/lectura-vehiculo")
    public ResponseEntity<DocumentoVehiculoLecturaResponse> leerVehiculo(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean forzar,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(documentoVehiculoLecturaService.leerVehiculo(id, forzar, usuarioLogueado));
    }

    @PatchMapping("/documentos/{id}/paginas")
    public ResponseEntity<Void> eliminarPaginas(
            @PathVariable Long id,
            @RequestParam String rangoPaginas,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        documentoService.eliminarPaginasDocumento(id, rangoPaginas, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documentos/{id}/extraer")
    public ResponseEntity<Void> extraerPaginas(
            @PathVariable Long id,
            @RequestParam String rangoPaginas,
            @RequestParam TipoDocumento tipoDocumento,
            @RequestParam(required = false) String nombreArchivo,
            @RequestParam(required = false) Long operacionId,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        documentoService.extraerPaginasDocumento(id, rangoPaginas, tipoDocumento, nombreArchivo, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/documentos/{id}/paginas")
    public ResponseEntity<Map<String, Integer>> contarPaginas(@PathVariable Long id, Authentication authentication) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(Map.of("totalPaginas", documentoService.contarPaginasDocumento(id, usuarioLogueado)));
    }

    @org.springframework.web.bind.annotation.GetMapping(value = "/documentos/{id}/paginas/{pagina}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previsualizarPagina(
            @PathVariable Long id,
            @PathVariable int pagina,
            Authentication authentication
    ) {
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
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
        Usuario usuarioLogueado = currentUserService.requireUser(authentication);
        List<Long> ids = Arrays.stream(documentoIds.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isBlank())
                .map(Long::valueOf)
                .toList();
        documentoService.unirDocumentos(id, ids, tipoDocumento, nombreArchivo, operacionId, usuarioLogueado);
        return ResponseEntity.noContent().build();
    }
}
