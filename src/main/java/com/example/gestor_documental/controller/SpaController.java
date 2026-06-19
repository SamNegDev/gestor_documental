package com.example.gestor_documental.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/admin/**",
            "/cliente/**",
            "/expedientes/**",
            "/solicitudes/**",
            "/acceso-denegado"
    })
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
