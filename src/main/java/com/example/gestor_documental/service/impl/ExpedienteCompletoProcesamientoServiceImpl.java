package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ProcesamientoExpedienteCompletoResponse;
import com.example.gestor_documental.dto.expediente.SolicitudDocumentacionIaResponse;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteCompletoProcesamientoService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.SolicitudDocumentacionIaService;
import com.example.gestor_documental.service.SolicitudService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpedienteCompletoProcesamientoServiceImpl implements ExpedienteCompletoProcesamientoService {

    private final DocumentoService documentoService;
    private final DocumentoRepository documentoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final SolicitudRepository solicitudRepository;
    private final ExpedienteService expedienteService;
    private final SolicitudService solicitudService;
    private final SolicitudDocumentacionIaService solicitudDocumentacionIaService;

    private final ConcurrentMap<String, JobState> jobs = new ConcurrentHashMap<>();

    @Value("${app.ocr.expediente-completo.concurrent-jobs:2}")
    private int concurrentJobs;

    private ExecutorService executor;

    @PostConstruct
    void iniciarExecutor() {
        int workers = Math.max(1, Math.min(concurrentJobs, 4));
        AtomicInteger threadNumber = new AtomicInteger(1);
        executor = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "expediente-completo-procesamiento-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        reencolarPendientesRecientes();
    }

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
    public ProcesamientoExpedienteCompletoResponse iniciarDocumentoExistente(Long documentoId, Usuario usuario) {
        Documento documento = documentoService.obtenerDocumentoConPermiso(documentoId, usuario);
        if (documento.getTipoDocumento() != TipoDocumento.EXPEDIENTE_COMPLETO) {
            throw new OperacionInvalidaException("Solo se puede procesar un expediente completo");
        }
        Long expedienteId = documento.getExpediente() != null ? documento.getExpediente().getId() : null;
        Long solicitudId = documento.getSolicitud() != null ? documento.getSolicitud().getId() : null;
        if (expedienteId == null && solicitudId == null) {
            throw new OperacionInvalidaException("El documento no pertenece a una solicitud o expediente");
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime ahora = LocalDateTime.now();
        JobState job = new JobState(
                jobId,
                expedienteId,
                solicitudId,
                documento.getId(),
                documento.getNombreArchivoOriginal(),
                solicitudId != null ? JobTarget.SOLICITUD : JobTarget.EXPEDIENTE,
                EstadoJob.PENDIENTE,
                0,
                "Expediente completo reencolado. Pendiente de separacion.",
                ahora,
                ahora
        );
        jobs.put(jobId, job);
        executor.submit(() -> procesar(jobId, usuario));
        return toResponse(job);
    }

    private void reencolarPendientesRecientes() {
        try {
            documentoRepository.findExpedientesCompletosPendientesDesde(LocalDateTime.now().minusHours(24), PageRequest.of(0, 20))
                    .forEach(documento -> {
                        try {
                            ProcesamientoExpedienteCompletoResponse job = iniciarDocumentoExistente(documento.getId(), documento.getSubidoPor());
                            log.info("Reencolado expediente completo pendiente {} como job {}", documento.getId(), job.jobId());
                        } catch (Exception exception) {
                            log.warn("No se pudo reencolar expediente completo pendiente {}", documento.getId(), exception);
                        }
                    });
        } catch (Exception exception) {
            log.warn("No se pudieron buscar expedientes completos pendientes para reencolar", exception);
        }
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
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void procesar(String jobId, Usuario usuario) {
        actualizar(jobId, EstadoJob.PROCESANDO, 0, "Separando el expediente completo en segundo plano.");
        try {
            JobState job = jobs.get(jobId);
            int generados = job.target() == JobTarget.SOLICITUD
                    ? documentoService.procesarExpedienteCompletoSolicitudDocumento(job.documentoId(), usuario)
                    : documentoService.procesarExpedienteCompletoDocumento(job.documentoId(), usuario);
            String lecturaIaMensaje = job.target() == JobTarget.SOLICITUD
                    ? intentarLecturaIaSolicitud(job.solicitudId(), usuario)
                    : null;
            actualizar(
                    jobId,
                    EstadoJob.COMPLETADO,
                    generados,
                    (generados > 0
                            ? "Separacion completada. Documentos generados: " + generados + "."
                            : "Separacion completada sin documentos detectados.")
                            + (lecturaIaMensaje != null ? " " + lecturaIaMensaje : "")
            );
        } catch (Exception exception) {
            actualizar(jobId, EstadoJob.ERROR, 0, exception.getMessage() != null
                    ? exception.getMessage()
                    : "No se pudo separar el expediente completo.");
        }
    }

    private String intentarLecturaIaSolicitud(Long solicitudId, Usuario usuario) {
        if (solicitudId == null) {
            return null;
        }
        try {
            SolicitudDocumentacionIaResponse response = solicitudDocumentacionIaService.procesarDocumentacionInterna(solicitudId, usuario);
            if (response.isDatosAplicados()) {
                return "Datos de interesados actualizados con IA.";
            }
            if (response.isYaEstabaCorrecta()) {
                return "Datos de interesados ya estaban correctos.";
            }
            return "Lectura IA realizada; requiere revision.";
        } catch (RuntimeException exception) {
            log.info("Solicitud {} separada, pero la lectura IA queda pendiente: {}", solicitudId, exception.getMessage());
            return "Lectura IA pendiente: " + (exception.getMessage() != null ? exception.getMessage() : "no se pudo completar") + ".";
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
