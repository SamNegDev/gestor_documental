package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.admin.AvisoAdminResponse;
import com.example.gestor_documental.dto.admin.AvisosAdminResumenResponse;
import com.example.gestor_documental.model.AvisoAdmin;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AvisoAdminRepository;
import com.example.gestor_documental.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/admin/avisos")
@RequiredArgsConstructor
public class AvisoAdminApiController {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AvisoAdminRepository avisoAdminRepository;
    private final CurrentUserService currentUserService;

    @GetMapping("/resumen")
    @Transactional(readOnly = true)
    public AvisosAdminResumenResponse resumen(Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return AvisosAdminResumenResponse.builder()
                .pendientes(avisoAdminRepository.countByLeidoFalse())
                .avisos(avisoAdminRepository.findByLeidoFalseOrderByFechaCreacionDesc(PageRequest.of(0, 8)).stream()
                        .map(this::map)
                        .toList())
                .build();
    }

    @PostMapping("/{id}/leer")
    @Transactional
    public AvisoAdminResponse marcarLeido(@PathVariable Long id, Authentication authentication) {
        Usuario admin = currentUserService.requireAdmin(authentication);
        AvisoAdmin aviso = avisoAdminRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aviso no encontrado."));
        aviso.setLeido(true);
        aviso.setFechaLectura(LocalDateTime.now());
        aviso.setLeidoPor(admin);
        return map(avisoAdminRepository.save(aviso));
    }

    @PostMapping("/leer-todos")
    @Transactional
    public void marcarTodosLeidos(Authentication authentication) {
        Usuario admin = currentUserService.requireAdmin(authentication);
        LocalDateTime ahora = LocalDateTime.now();
        avisoAdminRepository.findByLeidoFalseOrderByFechaCreacionDesc(PageRequest.of(0, 200)).forEach(aviso -> {
            aviso.setLeido(true);
            aviso.setFechaLectura(ahora);
            aviso.setLeidoPor(admin);
            avisoAdminRepository.save(aviso);
        });
    }

    private AvisoAdminResponse map(AvisoAdmin aviso) {
        return AvisoAdminResponse.builder()
                .id(aviso.getId())
                .tipo(aviso.getTipo())
                .titulo(aviso.getTitulo())
                .detalle(aviso.getDetalle())
                .origen(aviso.getOrigen())
                .fechaCreacion(aviso.getFechaCreacion() != null ? aviso.getFechaCreacion().format(FORMAT) : null)
                .expedienteId(aviso.getExpediente() != null ? aviso.getExpediente().getId() : null)
                .matricula(aviso.getExpediente() != null ? aviso.getExpediente().getMatricula() : null)
                .clienteId(aviso.getCliente() != null ? aviso.getCliente().getId() : null)
                .cliente(aviso.getCliente() != null ? aviso.getCliente().getNombre() : null)
                .build();
    }
}
