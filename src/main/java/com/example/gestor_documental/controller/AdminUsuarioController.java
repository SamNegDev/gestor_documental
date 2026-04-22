package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final UsuarioService usuarioService;
    private final ClienteService clienteService;
    private final PasswordEncoder passwordEncoder;

    @ModelAttribute("usuarioLogueado")
    public Usuario getUsuarioLogueado(Authentication authentication) {
        if (authentication == null) return null;
        return usuarioService.buscarPorEmail(authentication.getName());
    }

    @GetMapping
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("titulo", "Usuarios");
        model.addAttribute("subtitulo", "Gestión de usuarios del sistema");
        return "admin/lista_usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevoUsuario(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("roles", RolUsuario.values());
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("titulo", "Nuevo Usuario");
        model.addAttribute("subtitulo", "Crear un nuevo usuario");
        return "admin/usuario-form";
    }

    @GetMapping("/editar/{id}")
    public String editarUsuario(@PathVariable Long id, Model model) {
        Usuario usuario = usuarioService.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no válido: " + id));
                
        // Al editar, la contraseña no se envía al frontend.
        usuario.setPassword("");
        
        model.addAttribute("usuario", usuario);
        model.addAttribute("roles", RolUsuario.values());
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("titulo", "Editar Usuario");
        model.addAttribute("subtitulo", "Modificar datos del usuario");
        return "admin/usuario-form";
    }

    @PostMapping("/guardar")
    public String guardarUsuario(@ModelAttribute Usuario usuario, 
                                 @RequestParam(value = "clienteId", required = false) Long clienteId,
                                 RedirectAttributes redirectAttributes) {
        
        if (clienteId != null) {
            Cliente cliente = clienteService.buscarPorId(clienteId).orElse(null);
            usuario.setCliente(cliente);
        } else {
            usuario.setCliente(null);
        }

        if (usuario.getId() != null) {
            // Es edición
            Usuario existente = usuarioService.buscarPorId(usuario.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no válido: " + usuario.getId()));
            
            existente.setNombre(usuario.getNombre());
            existente.setApellidos(usuario.getApellidos());
            existente.setEmail(usuario.getEmail());
            existente.setRolUsuario(usuario.getRolUsuario());
            existente.setActivo(usuario.isActivo());
            existente.setCliente(usuario.getCliente());
            
            // Si llega una nueva clave, la codificamos y guardamos
            if (usuario.getPassword() != null && !usuario.getPassword().trim().isEmpty()) {
                existente.setPassword(passwordEncoder.encode(usuario.getPassword().trim()));
            }
            
            usuarioService.guardar(existente);
            redirectAttributes.addFlashAttribute("success", "Usuario modificado correctamente");
            
        } else {
            // Es nuevo usuario
            if (usuario.getPassword() == null || usuario.getPassword().trim().isEmpty()) {
                 usuario.setPassword(passwordEncoder.encode("Gestoria123!")); // Default en caso de venir vacio por algun motivo, aunque en front validaremos
            } else {
                 usuario.setPassword(passwordEncoder.encode(usuario.getPassword().trim()));
            }
            usuarioService.guardar(usuario);
            redirectAttributes.addFlashAttribute("success", "Usuario creado correctamente");
        }

        return "redirect:/admin/usuarios";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarUsuario(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Usuario usuario = usuarioService.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no válido: " + id));

        if (!usuario.isActivo()) {
            try {
                usuarioService.eliminarPorId(id);
                redirectAttributes.addFlashAttribute("success", "Usuario eliminado correctamente definitivamente.");
            } catch (Exception e) {
                 redirectAttributes.addFlashAttribute("error", "No se puede eliminar el usuario porque tiene registros asociados. (Se mantiene Inactivo).");
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "El usuario debe estar inactivo para poder ser eliminado.");
        }
        
        return "redirect:/admin/usuarios";
    }
}
