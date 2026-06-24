package com.example.gestor_documental.service;

import com.example.gestor_documental.dto.expediente.ProcesamientoExpedienteCompletoResponse;
import com.example.gestor_documental.model.Usuario;
import org.springframework.web.multipart.MultipartFile;

public interface ExpedienteCompletoProcesamientoService {

    ProcesamientoExpedienteCompletoResponse iniciar(Long expedienteId, MultipartFile archivo, Long operacionId, Usuario usuario);

    ProcesamientoExpedienteCompletoResponse iniciarSolicitud(Long solicitudId, MultipartFile archivo, Usuario usuario);

    ProcesamientoExpedienteCompletoResponse obtener(String jobId, Usuario usuario);
}
