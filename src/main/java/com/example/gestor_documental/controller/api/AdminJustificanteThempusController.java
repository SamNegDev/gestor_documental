package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.justificante.JustificanteThempusPreviewResponse;
import com.example.gestor_documental.dto.justificante.JustificanteThempusRequest;
import com.example.gestor_documental.dto.justificante.JustificanteThempusSendResponse;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.JustificanteThempusService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/justificantes-thempus")
@RequiredArgsConstructor
public class AdminJustificanteThempusController {

    private final JustificanteThempusService justificanteThempusService;
    private final CurrentUserService currentUserService;

    @GetMapping("/respaldo")
    public String consultarRespaldo(Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return justificanteThempusService.consultarRespaldo();
    }

    @PostMapping("/preview")
    public JustificanteThempusPreviewResponse preview(
            @RequestBody JustificanteThempusRequest request,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        return justificanteThempusService.preview(request);
    }

    @PostMapping("/enviar")
    public JustificanteThempusSendResponse enviar(
            @RequestBody JustificanteThempusRequest request,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        return justificanteThempusService.enviar(request);
    }
}
