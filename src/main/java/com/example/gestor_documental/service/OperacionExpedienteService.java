package com.example.gestor_documental.service;

import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.OperacionExpediente;

import java.util.List;

public interface OperacionExpedienteService {
    List<OperacionExpediente> sincronizarYListar(Expediente expediente);
}
