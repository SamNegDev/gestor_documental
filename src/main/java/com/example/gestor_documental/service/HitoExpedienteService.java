package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.model.HitoExpediente;
import com.example.gestor_documental.model.Usuario;

import java.util.List;

public interface HitoExpedienteService {
    List<HitoExpediente> listarPorExpediente(Long expedienteId);

    HitoExpediente completar(Long expedienteId, CodigoHitoExpediente codigo, Usuario usuario);

    void retroceder(Long expedienteId, CodigoHitoExpediente codigo, Usuario usuario);

    void retrocederFinalizacion(Long expedienteId, Usuario usuario);

    void finalizar(Long expedienteId, Usuario usuario);

    void abrirIncidencia(Long expedienteId, Long tipoIncidenciaId, String observaciones, Usuario usuario);
}
