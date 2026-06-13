package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.registro.VehiculoUpdateRequest;
import com.example.gestor_documental.model.Vehiculo;

public interface VehiculoService {
    Vehiculo obtenerOCrearPorMatricula(String matricula);
    Vehiculo actualizarPorMatricula(String matricula, VehiculoUpdateRequest request);
    int migrarExpedientesExistentes();
}
