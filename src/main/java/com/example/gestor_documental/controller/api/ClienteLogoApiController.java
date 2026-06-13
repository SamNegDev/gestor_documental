package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoLogoCliente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteLogoService;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.UsuarioService;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteLogoApiController {

    private final ClienteService clienteService;
    private final ClienteLogoService clienteLogoService;
    private final UsuarioService usuarioService;

    @GetMapping("/{id}/logos/{tipo}")
    public ResponseEntity<Resource> obtenerLogo(
            @PathVariable Long id,
            @PathVariable String tipo,
            Authentication authentication
    ) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario.getRolUsuario() != RolUsuario.ADMIN
                && (usuario.getCliente() == null || !usuario.getCliente().getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para ver este logo");
        }

        Cliente cliente = clienteService.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        Path path = clienteLogoService.resolver(cliente, TipoLogoCliente.fromRoute(tipo));
        MediaType mediaType = path.getFileName().toString().toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(path.toFile().length())
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(path));
    }
}
