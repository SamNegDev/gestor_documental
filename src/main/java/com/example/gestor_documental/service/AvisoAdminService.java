package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Expediente;

public interface AvisoAdminService {
    void crear(String tipo, String titulo, String detalle, String origen, Expediente expediente, Cliente cliente);
}
