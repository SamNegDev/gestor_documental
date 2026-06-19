package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.ia.ExtraccionGaPreviewResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaJobRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaJobResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaQueueItemResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaRevisionRequest;
import com.example.gestor_documental.dto.ia.ExtraccionGaRevisionResponse;
import com.example.gestor_documental.dto.ia.ExtraccionGaSincronizacionResponse;
import com.example.gestor_documental.model.Usuario;

import java.util.List;

public interface ExtraccionGaIaService {

    ExtraccionGaPreviewResponse preview(Long expedienteId, Usuario admin);

    ExtraccionGaResponse probar(Long expedienteId, ExtraccionGaRequest request, Usuario admin);

    ExtraccionGaResponse probarMultiple(Long expedienteId, ExtraccionGaRequest request, Usuario admin);

    ExtraccionGaRevisionResponse obtenerRevision(Long expedienteId, Usuario admin);

    ExtraccionGaRevisionResponse guardarRevision(Long expedienteId, ExtraccionGaRevisionRequest request, Usuario admin);

    ExtraccionGaRevisionResponse prepararRevision(Long expedienteId, Usuario admin);

    List<ExtraccionGaRevisionResponse> listarPreparadas(Usuario admin);

    List<ExtraccionGaQueueItemResponse> listarPendientesRevision(Usuario admin);

    List<ExtraccionGaJobResponse> crearJobs(ExtraccionGaJobRequest request, Usuario admin);

    List<ExtraccionGaJobResponse> listarJobsActivos(Usuario admin);

    ExtraccionGaJobResponse obtenerJob(Long jobId, Usuario admin);

    ExtraccionGaSincronizacionResponse sincronizarRevision(Long expedienteId, Usuario admin);

    byte[] exportarPreparadas(List<Long> expedienteIds, Usuario admin);
}
