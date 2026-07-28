package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.PagedResponse;
import com.example.gestor_documental.dto.geografia.CodigoPostalCatalogoResponse;
import com.example.gestor_documental.dto.geografia.DireccionSugerenciaResponse;
import com.example.gestor_documental.dto.geografia.MunicipioCatalogoResponse;
import com.example.gestor_documental.dto.geografia.ProvinciaCatalogoResponse;
import com.example.gestor_documental.service.CatalogoGeograficoService;
import com.example.gestor_documental.service.CartoCiudadDireccionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos/geografia")
@RequiredArgsConstructor
public class CatalogoGeograficoApiController {

    private final CatalogoGeograficoService catalogoGeograficoService;
    private final CartoCiudadDireccionService cartoCiudadDireccionService;

    @GetMapping("/provincias")
    public List<ProvinciaCatalogoResponse> provincias() {
        return catalogoGeograficoService.provincias();
    }

    @GetMapping("/municipios")
    public PagedResponse<MunicipioCatalogoResponse> municipios(
            @RequestParam(required = false) String provincia,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamanio
    ) {
        return catalogoGeograficoService.buscarMunicipios(provincia, q, pagina, tamanio);
    }

    @GetMapping("/codigos-postales")
    public List<CodigoPostalCatalogoResponse> codigosPostales(
            @RequestParam String provincia,
            @RequestParam String municipio
    ) {
        return catalogoGeograficoService.buscarCodigosPostales(provincia, municipio);
    }
    @GetMapping("/direcciones")
    public List<DireccionSugerenciaResponse> direcciones(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limite
    ) {
        String codigoPostal = q == null ? "" : q.replaceAll("\\D", "");
        if (codigoPostal.length() == 5) {
            return catalogoGeograficoService.buscarPorCodigoPostal(codigoPostal).stream()
                    .limit(Math.max(1, Math.min(limite, 100)))
                    .map(item -> new DireccionSugerenciaResponse(
                            item.codigoPostal(), item.municipio(), item.localidad(), item.provincia(), ""
                    ))
                    .toList();
        }
        return cartoCiudadDireccionService.sugerencias(q, limite);
    }
}
