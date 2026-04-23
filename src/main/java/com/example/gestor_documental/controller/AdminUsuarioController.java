package com.example.gestor_documental.controller;

import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
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
        
        if (usuario.getId() != null) {
            usuarioService.actualizarUsuario(usuario.getId(), usuario, clienteId, usuario.getPassword());
            redirectAttributes.addFlashAttribute("success", "Usuario modificado correctamente");
        } else {
            usuarioService.crearUsuario(usuario, clienteId, usuario.getPassword());
            redirectAttributes.addFlashAttribute("success", "Usuario creado correctamente");
        }

        return "redirect:/admin/usuarios";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarUsuario(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.eliminarUsuarioSeguro(id);
            redirectAttributes.addFlashAttribute("success", "Usuario eliminado correctamente definitivamente.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se puede eliminar el usuario porque tiene registros asociados. (Se mantiene Inactivo).");
        }
        
        return "redirect:/admin/usuarios";
    }
}
