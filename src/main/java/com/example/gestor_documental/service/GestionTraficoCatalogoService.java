package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.catalogo.CatalogoGestionResumenResponse;
import com.example.gestor_documental.dto.catalogo.GestionPersonaCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.GestionRepresentanteCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.GestionVehiculoCatalogoResponse;
import com.example.gestor_documental.dto.catalogo.ImportacionCatalogoResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface GestionTraficoCatalogoService {

    CatalogoGestionResumenResponse resumen();

    ImportacionCatalogoResponse importarPersonas(MultipartFile archivo);

    ImportacionCatalogoResponse importarRepresentantes(MultipartFile archivo);

    ImportacionCatalogoResponse importarVehiculos(MultipartFile archivo);

    List<GestionPersonaCatalogoResponse> buscarPersonas(String q, int limit);

    List<GestionRepresentanteCatalogoResponse> buscarRepresentantes(String q, int limit);

    List<GestionVehiculoCatalogoResponse> buscarVehiculos(String q, int limit);
}
