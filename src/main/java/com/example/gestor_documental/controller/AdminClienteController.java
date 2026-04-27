package com.example.gestor_documental.controller;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/clientes")
@RequiredArgsConstructor
public class AdminClienteController {

    private final ClienteService clienteService;
    private final UsuarioService usuarioService;

    @ModelAttribute("usuarioLogueado")
    public Usuario getUsuarioLogueado(Authentication authentication) {
        if (authentication == null) return null;
        return usuarioService.buscarPorEmail(authentication.getName());
    }

    @GetMapping
    public String listarClientes(Model model) {
        model.addAttribute("clientes", clienteService.listarTodos());
        model.addAttribute("titulo", "Clientes");
        model.addAttribute("subtitulo", "Gestión de clientes del sistema");
        return "admin/lista_clientes";
    }

    @GetMapping("/nuevo")
    public String nuevoCliente(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("titulo", "Nuevo Cliente");
        model.addAttribute("subtitulo", "Crear un nuevo cliente");
        return "admin/cliente-form";
    }

    @GetMapping("/editar/{id}")
    public String editarCliente(@PathVariable Long id, Model model) {
        Cliente cliente = clienteService.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no válido: " + id));
        model.addAttribute("cliente", cliente);
        model.addAttribute("titulo", "Editar Cliente");
        model.addAttribute("subtitulo", "Modificar datos del cliente");
        return "admin/cliente-form";
    }

    @PostMapping("/guardar")
    public String guardarCliente(@ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        if (cliente.getId() != null) {
            clienteService.guardar(cliente);
            redirectAttributes.addFlashAttribute("success", "Cliente modificado correctamente");
        } else {
            clienteService.guardar(cliente);
            redirectAttributes.addFlashAttribute("success", "Cliente creado correctamente");
        }
        return "redirect:/admin/clientes";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarCliente(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clienteService.eliminar(id);
            redirectAttributes.addFlashAttribute("success", "Cliente eliminado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "No se puede eliminar el cliente porque tiene registros asociados.");
        }
        return "redirect:/admin/clientes";
    }
}
