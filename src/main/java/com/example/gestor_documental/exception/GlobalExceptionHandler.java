package com.example.gestor_documental.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public String handleNotFound(RecursoNoEncontradoException ex, Model model) {
        model.addAttribute("titulo", "Recurso no encontrado");
        model.addAttribute("mensaje", ex.getMessage());
        model.addAttribute("tipo", "warning");
        return "error/error";
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public String handleAccessDenied(AccesoDenegadoException ex, Model model) {
        model.addAttribute("titulo", "Acceso denegado");
        model.addAttribute("mensaje", ex.getMessage());
        model.addAttribute("tipo", "danger");
        return "error/error";
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public String handleInvalidOperation(OperacionInvalidaException ex, Model model) {
        model.addAttribute("titulo", "Operación no permitida");
        model.addAttribute("mensaje", ex.getMessage());
        model.addAttribute("tipo", "warning");
        return "error/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model) {
        log.error("Error no controlado", ex);
        model.addAttribute("titulo", "Se ha producido un error");
        model.addAttribute("mensaje", "Ha ocurrido un problema inesperado en la aplicación.");
        model.addAttribute("tipo", "danger");
        return "error/error";
    }
}
