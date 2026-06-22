package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.justificante.JustificanteThempusPreviewResponse;
import com.example.gestor_documental.dto.justificante.JustificanteThempusRequest;
import com.example.gestor_documental.dto.justificante.JustificanteThempusSendResponse;

public interface JustificanteThempusService {

    String consultarRespaldo();

    JustificanteThempusPreviewResponse preview(JustificanteThempusRequest request);

    JustificanteThempusSendResponse enviar(JustificanteThempusRequest request);
}
