package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.ClienteAdminResponse;
import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.ClienteUpsertRequest;
import com.example.gestor_documental.dto.expediente.UsuarioAdminResponse;
import com.example.gestor_documental.dto.expediente.UsuarioCatalogsResponse;
import com.example.gestor_documental.dto.expediente.UsuarioUpsertRequest;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.UsuarioService;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminManagementApiController {

    private final ClienteService clienteService;
    private final UsuarioService usuarioService;

    @GetMapping("/clientes")
    public List<ClienteAdminResponse> listarClientes(Authentication authentication) {
        requireAdmin(authentication);
        return clienteService.listarTodos().stream().map(this::mapClienteAdmin).toList();
    }

    @GetMapping("/clientes/{id}")
    public ClienteAdminResponse obtenerCliente(@PathVariable Long id, Authentication authentication) {
        requireAdmin(authentication);
        return mapClienteAdmin(clienteService.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado")));
    }

    @PostMapping("/clientes")
    public ResponseEntity<Map<String, Long>> crearCliente(
            @RequestBody ClienteUpsertRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        validarCliente(request);
        Cliente creado = clienteService.guardar(mapCliente(request, new Cliente()));
        return ResponseEntity.created(URI.create("/admin/clientes/" + creado.getId()))
                .body(Map.of("id", creado.getId()));
    }

    @PutMapping("/clientes/{id}")
    public ResponseEntity<Void> actualizarCliente(
            @PathVariable Long id,
            @RequestBody ClienteUpsertRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        validarCliente(request);
        Cliente cliente = clienteService.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        clienteService.guardar(mapCliente(request, cliente));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clientes/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id, Authentication authentication) {
        requireAdmin(authentication);
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuarios")
    public List<UsuarioAdminResponse> listarUsuarios(Authentication authentication) {
        requireAdmin(authentication);
        return usuarioService.listarTodos().stream().map(this::mapUsuarioAdmin).toList();
    }

    @GetMapping("/usuarios/catalogos")
    public UsuarioCatalogsResponse obtenerCatalogosUsuarios(Authentication authentication) {
        requireAdmin(authentication);
        return UsuarioCatalogsResponse.builder()
                .roles(Arrays.stream(RolUsuario.values()).map(Enum::name).toList())
                .clientes(clienteService.listarTodos().stream().map(this::mapClienteResumen).toList())
                .build();
    }

    @GetMapping("/usuarios/{id}")
    public UsuarioAdminResponse obtenerUsuario(@PathVariable Long id, Authentication authentication) {
        requireAdmin(authentication);
        return mapUsuarioAdmin(usuarioService.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Map<String, Long>> crearUsuario(
            @RequestBody UsuarioUpsertRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        validarUsuario(request, true);
        Usuario creado = usuarioService.crearUsuario(mapUsuario(request, new Usuario()), request.getClienteId(), request.getPassword());
        return ResponseEntity.created(URI.create("/admin/usuarios/" + creado.getId()))
                .body(Map.of("id", creado.getId()));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<Void> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody UsuarioUpsertRequest request,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        validarUsuario(request, false);
        usuarioService.actualizarUsuario(id, mapUsuario(request, new Usuario()), request.getClienteId(), request.getPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Long id, Authentication authentication) {
        requireAdmin(authentication);
        usuarioService.eliminarUsuarioSeguro(id);
        return ResponseEntity.noContent().build();
    }

    private Usuario requireAdmin(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName());
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede realizar esta accion");
        }
        return usuario;
    }

    private void validarCliente(ClienteUpsertRequest request) {
        if (isBlank(request.getNif()) || isBlank(request.getNombre()) || isBlank(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NIF, nombre y email son obligatorios");
        }
    }

    private void validarUsuario(UsuarioUpsertRequest request, boolean creating) {
        if (isBlank(request.getNombre()) || isBlank(request.getApellidos()) || isBlank(request.getEmail()) || request.getRolUsuario() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, apellidos, email y rol son obligatorios");
        }
        if (creating && isBlank(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena es obligatoria para crear un usuario");
        }
        if (request.getRolUsuario() == RolUsuario.CLIENTE && request.getClienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un cliente para usuarios cliente");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Cliente mapCliente(ClienteUpsertRequest request, Cliente cliente) {
        cliente.setNif(clean(request.getNif()));
        cliente.setNombre(clean(request.getNombre()));
        cliente.setEmail(clean(request.getEmail()));
        cliente.setDireccion(clean(request.getDireccion()));
        cliente.setTelefono(clean(request.getTelefono()));
        return cliente;
    }

    private Usuario mapUsuario(UsuarioUpsertRequest request, Usuario usuario) {
        usuario.setNombre(clean(request.getNombre()));
        usuario.setApellidos(clean(request.getApellidos()));
        usuario.setEmail(clean(request.getEmail()));
        usuario.setRolUsuario(request.getRolUsuario());
        usuario.setActivo(request.isActivo());
        return usuario;
    }

    private ClienteAdminResponse mapClienteAdmin(Cliente cliente) {
        return ClienteAdminResponse.builder()
                .id(cliente.getId())
                .nif(cliente.getNif())
                .nombre(cliente.getNombre())
                .email(cliente.getEmail())
                .direccion(cliente.getDireccion())
                .telefono(cliente.getTelefono())
                .build();
    }

    private ClienteResumenResponse mapClienteResumen(Cliente cliente) {
        return ClienteResumenResponse.builder()
                .id(cliente.getId())
                .nombre(cliente.getNombre())
                .nif(cliente.getNif())
                .email(cliente.getEmail())
                .telefono(cliente.getTelefono())
                .build();
    }

    private UsuarioAdminResponse mapUsuarioAdmin(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String completo = (nombre + " " + apellidos).trim();
        return UsuarioAdminResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellidos(usuario.getApellidos())
                .nombreCompleto(!completo.isEmpty() ? completo : usuario.getEmail())
                .email(usuario.getEmail())
                .rol(usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null)
                .activo(usuario.isActivo())
                .cliente(usuario.getCliente() != null ? mapClienteResumen(usuario.getCliente()) : null)
                .build();
    }
}
