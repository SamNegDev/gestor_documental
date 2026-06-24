package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ProcesamientoExpedienteCompletoResponse;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteCompletoProcesamientoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.SolicitudService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class ExpedienteCompletoProcesamientoServiceImpl implements ExpedienteCompletoProcesamientoService {

    private final DocumentoService documentoService;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;

    private final ConcurrentMap<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "expediente-completo-procesamiento");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public ProcesamientoExpedienteCompletoResponse iniciar(Long expedienteId, MultipartFile archivo, Long operacionId, Usuario usuario) {
        Documento documento = documentoService.guardarExpedienteCompletoOriginalParaExpediente(expedienteId, archivo, operacionId, usuario);
        String jobId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();
        JobState job = new JobState(
                jobId,
                expedienteId,
                null,
                documento.getId(),
                documento.getNombreArchivoOriginal(),
                JobTarget.EXPEDIENTE,
                EstadoJob.PENDIENTE,
                0,
                "Expediente completo recibido. Pendiente de separacion.",
                ahora,
                ahora
        );
        jobs.put(jobId, job);
        executor.submit(() -> procesar(jobId, usuario));
        return toResponse(job);
    }

    @Override
    public ProcesamientoExpedienteCompletoResponse iniciarSolicitud(Long solicitudId, MultipartFile archivo, Usuario usuario) {
        Documento documento = documentoService.guardarExpedienteCompletoOriginalParaSolicitud(solicitudId, archivo, usuario);
        String jobId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();
        JobState job = new JobState(
                jobId,
                null,
                solicitudId,
                documento.getId(),
                documento.getNombreArchivoOriginal(),
                JobTarget.SOLICITUD,
                EstadoJob.PENDIENTE,
                0,
                "Expediente completo recibido. Pendiente de separacion.",
                ahora,
                ahora
        );
        jobs.put(jobId, job);
        executor.submit(() -> procesar(jobId, usuario));
        return toResponse(job);
    }

    @Override
    public ProcesamientoExpedienteCompletoResponse obtener(String jobId, Usuario usuario) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new RecursoNoEncontradoException("Procesamiento no encontrado");
        }
        validarPermiso(job, usuario);
        return toResponse(job);
    }

    @PreDestroy
    void cerrarExecutor() {
        executor.shutdownNow();
    }

    private void procesar(String jobId, Usuario usuario) {
        actualizar(jobId, EstadoJob.PROCESANDO, 0, "Separando el expediente completo en segundo plano.");
        try {
            JobState job = jobs.get(jobId);
            int generados = job.target() == JobTarget.SOLICITUD
                    ? documentoService.procesarExpedienteCompletoSolicitudDocumento(job.documentoId(), usuario)
                    : documentoService.procesarExpedienteCompletoDocumento(job.documentoId(), usuario);
            actualizar(
                    jobId,
                    EstadoJob.COMPLETADO,
                    generados,
                    generados > 0
                            ? "Separacion completada. Documentos generados: " + generados + "."
                            : "Separacion completada sin documentos detectados."
            );
        } catch (Exception exception) {
            actualizar(jobId, EstadoJob.ERROR, 0, exception.getMessage() != null
                    ? exception.getMessage()
                    : "No se pudo separar el expediente completo.");
        }
    }

    private void actualizar(String jobId, EstadoJob estado, int documentosGenerados, String mensaje) {
        jobs.computeIfPresent(jobId, (id, job) -> new JobState(
                job.jobId(),
                job.expedienteId(),
                job.solicitudId(),
                job.documentoId(),
                job.archivo(),
                job.target(),
                estado,
                documentosGenerados,
                mensaje,
                job.fechaCreacion(),
                LocalDateTime.now()
        ));
    }

    private void validarPermiso(JobState job, Usuario usuario) {
        if (job.target() == JobTarget.SOLICITUD) {
            Solicitud solicitud = solicitudRepository.findById(job.solicitudId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
            if (!solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
                throw new AccesoDenegadoException("No tienes permiso para consultar este procesamiento");
            }
            return;
        }
        Expediente expediente = expedienteRepository.findById(job.expedienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para consultar este procesamiento");
        }
    }

    private ProcesamientoExpedienteCompletoResponse toResponse(JobState job) {
        return new ProcesamientoExpedienteCompletoResponse(
                job.jobId(),
                job.expedienteId(),
                job.solicitudId(),
                job.documentoId(),
                job.archivo(),
                job.estado().name(),
                job.documentosGenerados(),
                job.mensaje(),
                job.fechaCreacion(),
                job.fechaActualizacion()
        );
    }

    private enum EstadoJob {
        PENDIENTE,
        PROCESANDO,
        COMPLETADO,
        ERROR
    }

    private enum JobTarget {
        EXPEDIENTE,
        SOLICITUD
    }

    private record JobState(
            String jobId,
            Long expedienteId,
            Long solicitudId,
            Long documentoId,
            String archivo,
            JobTarget target,
            EstadoJob estado,
            int documentosGenerados,
            String mensaje,
            LocalDateTime fechaCreacion,
            LocalDateTime fechaActualizacion
    ) {
    }
}
