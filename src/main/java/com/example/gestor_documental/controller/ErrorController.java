package com.example.gestor_documental.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorController {
    @GetMapping("/acceso-denegado")
    public String accesoDenegado(Model model) {
        model.addAttribute("titulo", "Acceso denegado");
        model.addAttribute("mensaje", "No tienes permisos para acceder a esta página");
        model.addAttribute("tipo", "danger");
        return "error/error";
    }
}
