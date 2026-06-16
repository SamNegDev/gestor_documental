package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoRequest;
import com.example.gestor_documental.dto.seguimiento.ConfiguracionSeguimientoResponse;
import com.example.gestor_documental.model.ConfiguracionSeguimiento;
import com.example.gestor_documental.model.Usuario;

public interface ConfiguracionSeguimientoService {
    ConfiguracionSeguimiento obtener();
    ConfiguracionSeguimientoResponse obtenerResponse(Usuario admin);
    ConfiguracionSeguimientoResponse actualizar(ConfiguracionSeguimientoRequest request, Usuario admin);
}
