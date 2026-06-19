package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.catalogo.CatalogoGestionResumenResponse;
import com.example.gestor_documental.dto.catalogo.GestionPersonaCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.GestionRepresentanteCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.GestionVehiculoCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.ImportacionCatalogoResponse;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.GestionTraficoCatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/catalogos-gestion-trafico")
@RequiredArgsConstructor
public class AdminGestionTraficoCatalogoController {

    private final GestionTraficoCatalogoService catalogoService;
    private final CurrentUserService currentUserService;

    @GetMapping("/resumen")
    public CatalogoGestionResumenResponse resumen(Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.resumen();
    }

    @PostMapping(value = "/personas/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacionCatalogoResponse importarPersonas(@RequestParam("archivo") MultipartFile archivo, Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.importarPersonas(archivo);
    }

    @PostMapping(value = "/representantes/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacionCatalogoResponse importarRepresentantes(@RequestParam("archivo") MultipartFile archivo, Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.importarRepresentantes(archivo);
    }

    @PostMapping(value = "/vehiculos/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacionCatalogoResponse importarVehiculos(@RequestParam("archivo") MultipartFile archivo, Authentication authentication) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.importarVehiculos(archivo);
    }

    @GetMapping("/personas")
    public List<GestionPersonaCatalogoResponse> buscarPersonas(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "25") int limit,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.buscarPersonas(q, limit);
    }

    @GetMapping("/representantes")
    public List<GestionRepresentanteCatalogoResponse> buscarRepresentantes(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "25") int limit,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.buscarRepresentantes(q, limit);
    }

    @GetMapping("/vehiculos")
    public List<GestionVehiculoCatalogoResponse> buscarVehiculos(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "25") int limit,
            Authentication authentication
    ) {
        currentUserService.requireAdmin(authentication);
        return catalogoService.buscarVehiculos(q, limit);
    }
}
