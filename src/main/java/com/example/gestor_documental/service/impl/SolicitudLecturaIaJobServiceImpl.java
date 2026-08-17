package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.*;
import com.example.gestor_documental.enums.*;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import com.example.gestor_documental.service.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudLecturaIaJobServiceImpl implements SolicitudLecturaIaJobService {
    private static final List<EstadoLecturaIaJob> ESTADOS_ACTIVOS = List.of(
            EstadoLecturaIaJob.PENDIENTE, EstadoLecturaIaJob.PROCESANDO);
    private static final List<EstadoLecturaIaItem> ITEMS_ACTIVOS = List.of(
            EstadoLecturaIaItem.PENDIENTE, EstadoLecturaIaItem.PROCESANDO);

    private final SolicitudLecturaIaJobRepository jobRepository;
    private final SolicitudLecturaIaItemRepository itemRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocumentoRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoIdentidadLecturaRepository identidadRepository;
    private final DocumentoRolesLecturaRepository rolesRepository;
    private final DocumentoVehiculoLecturaRepository vehiculoRepository;
    private final DocumentoIdentidadLecturaService identidadService;
    private final DocumentoRolesLecturaService rolesService;
    private final DocumentoVehiculoLecturaService vehiculoService;
    private final SolicitudDocumentacionIaService consolidacionService;
    private final SolicitudService solicitudService;
    private final PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "solicitud-lectura-ia");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    @Transactional
    public synchronized SolicitudLecturaIaJobResponse crear(
            Long solicitudId, Usuario usuario, boolean forzarRelectura, String origen, Long documentoId) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (usuario == null || !solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para procesar esta solicitud");
        }

        Optional<SolicitudLecturaIaJob> activo = jobRepository
                .findTopBySolicitudIdAndEstadoInOrderByFechaCreacionDescIdDesc(solicitudId, ESTADOS_ACTIVOS);
        if (activo.isPresent() && (origen == null || origen.isBlank() || "MANUAL".equalsIgnoreCase(origen))) {
            return SolicitudLecturaIaJobResponse.from(activo.get());
        }

        SolicitudLecturaIaJob job = new SolicitudLecturaIaJob();
        job.setSolicitud(solicitud);
        job.setCreadoPor(usuario);
        job.setOrigen(origen == null || origen.isBlank() ? "MANUAL" : origen);
        job.setForzarRelectura(forzarRelectura);
        job.setFaseActual("Preparando documentos");

        for (Documento documento : documentoRepository.findBySolicitudId(solicitudId)) {
            if (documentoId != null && !documentoId.equals(documento.getId())) continue;
            TipoLecturaIa tipo = tipoLectura(documento.getTipoDocumento());
            if (tipo == null || itemRepository.existsByDocumentoIdAndEstadoIn(documento.getId(), ITEMS_ACTIVOS)) {
                continue;
            }
            if (!forzarRelectura && lecturaExistente(documento.getId(), tipo)) {
                continue;
            }
            SolicitudLecturaIaItem item = new SolicitudLecturaIaItem();
            item.setDocumento(documento);
            item.setTipoLectura(tipo);
            item.setVersionPrompt(tipo == TipoLecturaIa.IDENTIDAD ? "IDENTIDAD_V2" : "LECTURA_V1");
            job.addItem(item);
        }

        job.setTotalItems(job.getItems().size());
        if (job.getTotalItems() == 0 && activo.isPresent()) {
            return SolicitudLecturaIaJobResponse.from(activo.get());
        }
        if (job.getTotalItems() == 0) {
            job.setEstado(EstadoLecturaIaJob.COMPLETADO);
            job.setProgreso(100);
            job.setFaseActual("Lectura al día");
            job.setMensaje("No hay documentos pendientes de lectura.");
            job.setFechaFin(LocalDateTime.now());
        } else {
            job.setEstado(EstadoLecturaIaJob.PENDIENTE);
            job.setMensaje(job.getTotalItems() == 1
                    ? "1 documento en cola de lectura."
                    : job.getTotalItems() + " documentos en cola de lectura.");
        }

        SolicitudLecturaIaJob guardado = jobRepository.saveAndFlush(job);
        if (guardado.getEstado().activo()) {
            Long jobId = guardado.getId();
            if (jobId == null) {
                throw new IllegalStateException("No se pudo identificar el trabajo de lectura IA creado");
            }
            programarTrasCommit(jobId);
        }
        return SolicitudLecturaIaJobResponse.from(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitudLecturaIaJobResponse obtenerUltimo(Long solicitudId, Usuario usuario) {
        Solicitud solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud no encontrada"));
        if (usuario == null || !solicitudService.tienePermisoSolicitud(solicitud, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para consultar esta lectura");
        }
        return jobRepository.findTopBySolicitudIdOrderByFechaCreacionDescIdDesc(solicitudId)
                .map(SolicitudLecturaIaJobResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void crearAutomatico(Long solicitudId, Long usuarioId, String origen) {
        if (solicitudId == null || usuarioId == null) return;
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            try {
                crear(solicitudId, usuario, false, origen, null);
            } catch (RuntimeException ex) {
                log.warn("No se pudo encolar lectura IA automatica de solicitud {}: {}", solicitudId, ex.getMessage());
            }
        });
    }

    private void programarTrasCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(() -> procesar(jobId));
                }
            });
        } else {
            executor.execute(() -> procesar(jobId));
        }
    }

    private void procesar(Long jobId) {
        if (jobId == null) {
            log.error("Se intento procesar un trabajo de lectura IA sin identificador");
            return;
        }
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            Long usuarioId = tx.execute(status -> iniciarJob(jobId));
            if (usuarioId == null) return;
            Usuario usuario = usuarioRepository.findById(usuarioId).orElse(null);
            if (usuario == null) {
                finalizarConError(jobId, "No se encuentra el usuario que solicito la lectura.");
                return;
            }

            List<Long> itemIds = tx.execute(status -> itemRepository.findByJobIdOrderById(jobId).stream()
                    .map(SolicitudLecturaIaItem::getId).toList());
            if (itemIds == null) itemIds = List.of();
            for (Long itemId : itemIds) {
                procesarItem(itemId, usuario, tx);
            }

            Long solicitudId = tx.execute(status -> jobRepository.findById(jobId)
                    .map(job -> job.getSolicitud().getId()).orElse(null));
            if (solicitudId != null) {
                try {
                    consolidacionService.procesarDocumentacion(solicitudId, usuario, false);
                } catch (RuntimeException ex) {
                    log.warn("Lecturas completadas pero fallo la consolidacion de solicitud {}: {}", solicitudId, ex.getMessage());
                }
            }
            tx.executeWithoutResult(status -> finalizarJob(jobId));
        } catch (RuntimeException ex) {
            log.error("Error procesando trabajo de lectura IA {}", jobId, ex);
            finalizarConError(jobId, mensajeSeguro(ex));
        }
    }

    private Long iniciarJob(Long jobId) {
        SolicitudLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getEstado().activo()) return null;
        job.setEstado(EstadoLecturaIaJob.PROCESANDO);
        job.setFechaInicio(job.getFechaInicio() != null ? job.getFechaInicio() : LocalDateTime.now());
        job.setFaseActual("Leyendo documentacion");
        job.setMensaje("La lectura se ejecuta en segundo plano. Puedes seguir revisando la solicitud.");
        jobRepository.save(job);
        return job.getCreadoPor() != null ? job.getCreadoPor().getId() : null;
    }

    private void procesarItem(Long itemId, Usuario usuario, TransactionTemplate tx) {
        ItemContext context = tx.execute(status -> {
            SolicitudLecturaIaItem item = itemRepository.findById(itemId).orElse(null);
            if (item == null || !item.getEstado().activo()) return null;
            item.setEstado(EstadoLecturaIaItem.PROCESANDO);
            item.setIntentos(item.getIntentos() + 1);
            item.setFechaInicio(LocalDateTime.now());
            item.setMensaje("Analizando documento");
            itemRepository.save(item);
            recalcularJob(item.getJob().getId());
            return new ItemContext(item.getDocumento().getId(), item.getTipoLectura(), item.getJob().isForzarRelectura());
        });
        if (context == null) return;

        LocalDateTime inicio = LocalDateTime.now();
        ResultadoItem resultado;
        try {
            resultado = leer(context, usuario);
        } catch (RuntimeException ex) {
            resultado = new ResultadoItem(EstadoLecturaIaItem.ERROR, null, null, mensajeSeguro(ex));
        }
        ResultadoItem finalResultado = resultado;
        tx.executeWithoutResult(status -> {
            SolicitudLecturaIaItem item = itemRepository.findById(itemId).orElse(null);
            if (item == null) return;
            item.setEstado(finalResultado.estado());
            item.setModelo(finalResultado.modelo());
            item.setConfianza(finalResultado.confianza());
            item.setMensaje(finalResultado.mensaje());
            item.setFechaFin(LocalDateTime.now());
            item.setDuracionMs(Duration.between(inicio, item.getFechaFin()).toMillis());
            itemRepository.save(item);
            recalcularJob(item.getJob().getId());
        });
    }

    ResultadoItem leer(ItemContext context, Usuario usuario) {
        try {
            if (context.tipo() == TipoLecturaIa.IDENTIDAD) {
                DocumentoIdentidadLecturaResponse response = identidadService.leerIdentidad(
                        context.documentoId(), context.forzar(), usuario);
                return resultado(response.isRequiereRevision(), response.getModelo(), response.getConfianzaGlobal(), response.getMensaje());
            }
            if (context.tipo() == TipoLecturaIa.ROLES) {
                DocumentoRolesLecturaResponse response = rolesService.leerRoles(
                        context.documentoId(), context.forzar(), usuario);
                return resultado(response.isRequiereRevision(), response.getModelo(), response.getConfianzaGlobal(), response.getMensaje());
            }
            DocumentoVehiculoLecturaResponse response = vehiculoService.leerVehiculo(
                    context.documentoId(), context.forzar(), usuario);
            return resultado(response.isRequiereRevision(), response.getModelo(), response.getConfianzaGlobal(), response.getMensaje());
        } catch (RuntimeException exception) {
            ResultadoItem reutilizado = resultadoPersistidoPorOtroProceso(context, exception);
            if (reutilizado != null) {
                log.info("Lectura IA de documento {} ya persistida por otro proceso; se reutiliza el resultado",
                        context.documentoId());
                return reutilizado;
            }
            throw exception;
        }
    }

    private ResultadoItem resultadoPersistidoPorOtroProceso(ItemContext context, RuntimeException exception) {
        if (!esConflictoLecturaConcurrente(exception)) {
            return null;
        }
        return resultadoPersistido(context);
    }

    private ResultadoItem resultadoPersistido(ItemContext context) {
        String mensaje = "Lectura ya completada por otro proceso; se reutilizo el resultado disponible.";
        return switch (context.tipo()) {
            case IDENTIDAD -> identidadRepository.findByDocumentoId(context.documentoId())
                    .map(DocumentoIdentidadLecturaResponse::from)
                    .map(response -> resultado(response.isRequiereRevision(), response.getModelo(),
                            response.getConfianzaGlobal(), mensaje))
                    .orElse(null);
            case ROLES -> rolesRepository.findByDocumentoId(context.documentoId())
                    .map(DocumentoRolesLecturaResponse::from)
                    .map(response -> resultado(response.isRequiereRevision(), response.getModelo(),
                            response.getConfianzaGlobal(), mensaje))
                    .orElse(null);
            case VEHICULO -> vehiculoRepository.findByDocumentoId(context.documentoId())
                    .map(DocumentoVehiculoLecturaResponse::from)
                    .map(response -> resultado(response.isRequiereRevision(), response.getModelo(),
                            response.getConfianzaGlobal(), mensaje))
                    .orElse(null);
        };
    }

    private boolean esConflictoLecturaConcurrente(Throwable throwable) {
        boolean integridad = false;
        boolean duplicado = false;
        Throwable actual = throwable;
        while (actual != null) {
            integridad |= actual instanceof DataIntegrityViolationException;
            String mensaje = actual.getMessage();
            duplicado |= mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains("duplicate entry");
            actual = actual.getCause();
        }
        return integridad && duplicado;
    }

    private ResultadoItem resultado(boolean revision, String modelo, Double confianza, String mensaje) {
        return new ResultadoItem(revision ? EstadoLecturaIaItem.REQUIERE_REVISION : EstadoLecturaIaItem.COMPLETADO,
                modelo, confianza, mensaje);
    }

    private void recalcularJob(Long jobId) {
        SolicitudLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        List<SolicitudLecturaIaItem> items = itemRepository.findByJobIdOrderById(jobId);
        int procesados = (int) items.stream().filter(item -> !item.getEstado().activo()).count();
        int revision = (int) items.stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.REQUIERE_REVISION).count();
        int errores = (int) items.stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.ERROR).count();
        job.setItemsProcesados(procesados);
        job.setItemsRevision(revision);
        job.setItemsError(errores);
        job.setProgreso(job.getTotalItems() == 0 ? 100 : Math.min(99, procesados * 100 / job.getTotalItems()));
        job.setFaseActual(procesados < job.getTotalItems()
                ? "Leyendo documento " + (procesados + 1) + " de " + job.getTotalItems()
                : "Consolidando datos");
        jobRepository.save(job);
    }

    private void finalizarJob(Long jobId) {
        SolicitudLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        recalcularJob(jobId);
        job = jobRepository.findById(jobId).orElseThrow();
        if (job.getItemsError() >= job.getTotalItems() && job.getTotalItems() > 0) {
            job.setEstado(EstadoLecturaIaJob.ERROR);
            job.setFaseActual("Lectura fallida");
            job.setMensaje("No se pudo leer ningun documento. Revisa los errores y vuelve a intentarlo.");
        } else if (job.getItemsRevision() > 0 || job.getItemsError() > 0) {
            job.setEstado(EstadoLecturaIaJob.REQUIERE_REVISION);
            job.setFaseActual("Lectura completada con revisiones");
            job.setMensaje("Lectura terminada. Hay documentos que necesitan revision.");
        } else {
            job.setEstado(EstadoLecturaIaJob.COMPLETADO);
            job.setFaseActual("Lectura completada");
            job.setMensaje("Todos los documentos compatibles se han leido correctamente.");
        }
        job.setProgreso(100);
        if (job.getFechaFin() == null) {
            job.setFechaFin(LocalDateTime.now());
        }
        jobRepository.save(job);
    }

    private void finalizarConError(Long jobId, String mensaje) {
        if (jobId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SolicitudLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) return;
            job.setEstado(EstadoLecturaIaJob.ERROR);
            job.setFaseActual("Error de lectura");
            job.setMensaje(mensaje);
            job.setFechaFin(LocalDateTime.now());
            jobRepository.save(job);
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recuperarTrabajosInterrumpidos() {
        for (SolicitudLecturaIaJob job : jobRepository.findByEstadoInOrderByFechaCreacionAsc(ESTADOS_ACTIVOS)) {
            job.setEstado(EstadoLecturaIaJob.PENDIENTE);
            job.setFaseActual("Reanudando lectura");
            job.getItems().stream()
                    .filter(item -> item.getEstado() == EstadoLecturaIaItem.PROCESANDO)
                    .forEach(item -> {
                        item.setEstado(EstadoLecturaIaItem.PENDIENTE);
                        item.setMensaje("Lectura reanudada tras reinicio");
                    });
            jobRepository.save(job);
            programarTrasCommit(job.getId());
        }
        repararErroresConcurrentes();
    }

    void repararErroresConcurrentes() {
        Set<Long> trabajosRecalculados = new LinkedHashSet<>();
        int reparados = 0;
        for (SolicitudLecturaIaItem item : itemRepository.findByEstadoAndMensajeContainingIgnoreCase(
                EstadoLecturaIaItem.ERROR, "Duplicate entry")) {
            if (item.getDocumento() == null || item.getDocumento().getId() == null || item.getTipoLectura() == null) {
                continue;
            }
            ResultadoItem resultado = resultadoPersistido(new ItemContext(
                    item.getDocumento().getId(), item.getTipoLectura(), false));
            if (resultado == null) {
                continue;
            }
            item.setEstado(resultado.estado());
            item.setModelo(resultado.modelo());
            item.setConfianza(resultado.confianza());
            item.setMensaje(resultado.mensaje());
            itemRepository.save(item);
            if (item.getJob() != null && item.getJob().getId() != null) {
                trabajosRecalculados.add(item.getJob().getId());
            }
            reparados++;
        }
        trabajosRecalculados.forEach(this::finalizarJob);
        if (reparados > 0) {
            log.info("Reparados {} elementos de lectura duplicados en {} trabajos de solicitud",
                    reparados, trabajosRecalculados.size());
        }
    }

    @PreDestroy
    public void cerrarExecutor() {
        executor.shutdown();
    }

    private TipoLecturaIa tipoLectura(TipoDocumento tipo) {
        if (tipo == TipoDocumento.DNI || tipo == TipoDocumento.CIF) return TipoLecturaIa.IDENTIDAD;
        if (tipo == TipoDocumento.CONTRATO_COMPRAVENTA || tipo == TipoDocumento.FACTURA) return TipoLecturaIa.ROLES;
        if (tipo == TipoDocumento.PERMISO_CIRCULACION || tipo == TipoDocumento.FICHA_TECNICA
                || tipo == TipoDocumento.INFORME_DGT) return TipoLecturaIa.VEHICULO;
        return null;
    }

    private boolean lecturaExistente(Long documentoId, TipoLecturaIa tipo) {
        return switch (tipo) {
            case IDENTIDAD -> identidadRepository.findByDocumentoId(documentoId).isPresent();
            case ROLES -> rolesRepository.findByDocumentoId(documentoId).isPresent();
            case VEHICULO -> vehiculoRepository.findByDocumentoId(documentoId).isPresent();
        };
    }

    String mensajeSeguro(Throwable throwable) {
        String value = throwable.getMessage();
        if (value == null || value.isBlank()) value = "Error inesperado durante la lectura.";
        String normalizado = value.toLowerCase(Locale.ROOT);
        if (normalizado.contains("could not execute statement")
                || normalizado.contains("duplicate entry")
                || normalizado.contains("sql [")
                || normalizado.contains("constraint [")) {
            return "No se pudo guardar la lectura. Vuelve a intentarlo.";
        }
        return value.length() > 950 ? value.substring(0, 950) : value;
    }

    record ItemContext(Long documentoId, TipoLecturaIa tipo, boolean forzar) {}
    record ResultadoItem(EstadoLecturaIaItem estado, String modelo, Double confianza, String mensaje) {}
}
