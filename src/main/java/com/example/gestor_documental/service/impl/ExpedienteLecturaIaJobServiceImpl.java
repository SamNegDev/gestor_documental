package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ExpedienteLecturaIaJobResponse;
import com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse;
import com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse;
import com.example.gestor_documental.enums.*;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.*;
import com.example.gestor_documental.repository.*;
import com.example.gestor_documental.service.ExpedienteLecturaIaJobService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.DocumentoIdentidadLecturaService;
import com.example.gestor_documental.service.DocumentoRolesLecturaService;
import com.example.gestor_documental.service.DocumentoVehiculoLecturaService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpedienteLecturaIaJobServiceImpl implements ExpedienteLecturaIaJobService {
    private static final List<EstadoLecturaIaJob> ESTADOS_ACTIVOS = List.of(
            EstadoLecturaIaJob.PENDIENTE, EstadoLecturaIaJob.PROCESANDO);
    private static final List<EstadoLecturaIaItem> ITEMS_ACTIVOS = List.of(
            EstadoLecturaIaItem.PENDIENTE, EstadoLecturaIaItem.PROCESANDO);

    private final ExpedienteLecturaIaJobRepository jobRepository;
    private final ExpedienteLecturaIaItemRepository itemRepository;
    private final ExpedienteRepository expedienteRepository;
    private final DocumentoRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoIdentidadLecturaRepository identidadRepository;
    private final DocumentoRolesLecturaRepository rolesRepository;
    private final DocumentoVehiculoLecturaRepository vehiculoRepository;
    private final DocumentoIdentidadLecturaService identidadService;
    private final DocumentoRolesLecturaService rolesService;
    private final DocumentoVehiculoLecturaService vehiculoService;
    private final ExpedienteDocumentacionActualizacionService actualizacionService;
    private final ExpedienteService expedienteService;
    private final PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "expediente-lectura-ia");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    @Transactional
    public synchronized ExpedienteLecturaIaJobResponse crear(
            Long expedienteId, Usuario usuario, boolean forzarRelectura, String origen) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (usuario == null || !expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para procesar este expediente");
        }

        Optional<ExpedienteLecturaIaJob> activo = jobRepository
                .findTopByExpedienteIdAndEstadoInOrderByFechaCreacionDescIdDesc(expedienteId, ESTADOS_ACTIVOS);
        if (activo.isPresent() && (origen == null || origen.isBlank() || "MANUAL".equalsIgnoreCase(origen))) {
            return ExpedienteLecturaIaJobResponse.from(activo.get());
        }

        ExpedienteLecturaIaJob job = new ExpedienteLecturaIaJob();
        job.setExpediente(expediente);
        job.setCreadoPor(usuario);
        job.setOrigen(origen == null || origen.isBlank() ? "MANUAL" : origen);
        job.setForzarRelectura(forzarRelectura);
        job.setFaseActual("Preparando documentos");

        for (Documento documento : documentoRepository.findByExpedienteId(expedienteId)) {
            TipoLecturaIa tipo = tipoLectura(documento.getTipoDocumento());
            if (tipo == null || itemRepository.existsByDocumentoIdAndEstadoIn(documento.getId(), ITEMS_ACTIVOS)) continue;
            if (!forzarRelectura && lecturaExistente(documento.getId(), tipo)) continue;
            ExpedienteLecturaIaItem item = new ExpedienteLecturaIaItem();
            item.setDocumento(documento);
            item.setTipoLectura(tipo);
            item.setVersionPrompt(tipo == TipoLecturaIa.IDENTIDAD ? "IDENTIDAD_V2" : "LECTURA_V1");
            job.addItem(item);
        }

        job.setTotalItems(job.getItems().size());
        if (job.getTotalItems() == 0 && activo.isPresent()) {
            return ExpedienteLecturaIaJobResponse.from(activo.get());
        }
        if (job.getTotalItems() == 0) {
            job.setEstado(EstadoLecturaIaJob.COMPLETADO);
            job.setProgreso(100);
            job.setFaseActual("Lectura al dia");
            job.setMensaje("No hay documentos pendientes de lectura.");
            job.setFechaFin(LocalDateTime.now());
        } else {
            job.setEstado(EstadoLecturaIaJob.PENDIENTE);
            job.setMensaje(job.getTotalItems() == 1
                    ? "1 documento en cola de lectura."
                    : job.getTotalItems() + " documentos en cola de lectura.");
        }

        ExpedienteLecturaIaJob guardado = jobRepository.saveAndFlush(job);
        if (guardado.getEstado().activo()) programarTrasCommit(guardado.getId());
        return ExpedienteLecturaIaJobResponse.from(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpedienteLecturaIaJobResponse obtenerUltimo(Long expedienteId, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));
        if (usuario == null || !expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para consultar esta lectura");
        }
        return jobRepository.findTopByExpedienteIdOrderByFechaCreacionDescIdDesc(expedienteId)
                .map(ExpedienteLecturaIaJobResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void crearAutomatico(Long expedienteId, Long usuarioId, String origen) {
        if (expedienteId == null || usuarioId == null) return;
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            try {
                crear(expedienteId, usuario, false, origen);
            } catch (RuntimeException ex) {
                log.warn("No se pudo encolar lectura IA automatica de expediente {}: {}", expedienteId, ex.getMessage());
            }
        });
    }

    private void programarTrasCommit(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { executor.execute(() -> procesar(jobId)); }
            });
        } else {
            executor.execute(() -> procesar(jobId));
        }
    }

    private void procesar(Long jobId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            JobContext context = tx.execute(status -> iniciarJob(jobId));
            if (context == null) return;
            Usuario usuario = usuarioRepository.findById(context.usuarioId()).orElse(null);
            if (usuario == null) {
                finalizarConError(jobId, "No se encuentra el usuario que solicito la lectura.");
                return;
            }
            List<Long> itemIds = tx.execute(status -> itemRepository.findByJobIdOrderById(jobId).stream()
                    .map(ExpedienteLecturaIaItem::getId).toList());
            if (itemIds == null) itemIds = List.of();
            for (Long itemId : itemIds) {
                procesarItem(itemId, usuario, tx);
            }
            tx.executeWithoutResult(status -> marcarConsolidacion(jobId));
            actualizacionService.actualizarDesdeDocumentos(context.expedienteId(), usuario, context.forzar());
            tx.executeWithoutResult(status -> finalizarJob(jobId));
        } catch (RuntimeException ex) {
            log.error("Error procesando trabajo de lectura IA de expediente {}", jobId, ex);
            finalizarConError(jobId, mensajeSeguro(ex));
        }
    }

    private JobContext iniciarJob(Long jobId) {
        ExpedienteLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getEstado().activo()) return null;
        job.setEstado(EstadoLecturaIaJob.PROCESANDO);
        job.setFechaInicio(job.getFechaInicio() != null ? job.getFechaInicio() : LocalDateTime.now());
        job.setFaseActual("Leyendo documentacion");
        job.setMensaje("La lectura se ejecuta en segundo plano. Puedes seguir trabajando en el expediente.");
        jobRepository.save(job);
        return job.getCreadoPor() != null
                ? new JobContext(job.getExpediente().getId(), job.getCreadoPor().getId(), job.isForzarRelectura())
                : null;
    }

    private void procesarItem(Long itemId, Usuario usuario, TransactionTemplate tx) {
        ItemContext context = tx.execute(status -> {
            ExpedienteLecturaIaItem item = itemRepository.findById(itemId).orElse(null);
            if (item == null || !item.getEstado().activo()) return null;
            item.setEstado(EstadoLecturaIaItem.PROCESANDO);
            item.setIntentos(item.getIntentos() + 1);
            item.setFechaInicio(LocalDateTime.now());
            item.setMensaje("Analizando documento");
            itemRepository.save(item);
            return new ItemContext(item.getDocumento().getId(), item.getTipoLectura(), item.getJob().isForzarRelectura());
        });
        if (context == null) return;

        LocalDateTime inicio = LocalDateTime.now();
        Resultado resultado;
        try {
            resultado = leer(context, usuario);
        } catch (RuntimeException ex) {
            resultado = error(mensajeSeguro(ex));
        }
        Resultado finalResultado = resultado;
        tx.executeWithoutResult(status -> {
            ExpedienteLecturaIaItem item = itemRepository.findById(itemId).orElse(null);
            if (item == null) return;
            item.setEstado(finalResultado.estado());
            item.setModelo(finalResultado.modelo());
            item.setConfianza(finalResultado.confianza());
            item.setMensaje(finalResultado.mensaje());
            item.setFechaFin(LocalDateTime.now());
            item.setDuracionMs(Duration.between(inicio, item.getFechaFin()).toMillis());
            itemRepository.save(item);
            recalcularProgreso(item.getJob().getId());
        });
    }

    private Resultado leer(ItemContext context, Usuario usuario) {
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
    }

    private void recalcularProgreso(Long jobId) {
        ExpedienteLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        List<ExpedienteLecturaIaItem> items = itemRepository.findByJobIdOrderById(jobId);
        int procesados = (int) items.stream().filter(item -> !item.getEstado().activo()).count();
        int revision = (int) items.stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.REQUIERE_REVISION).count();
        int errores = (int) items.stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.ERROR).count();
        job.setItemsProcesados(procesados);
        job.setItemsRevision(revision);
        job.setItemsError(errores);
        job.setProgreso(job.getTotalItems() == 0 ? 100 : Math.min(90, procesados * 90 / job.getTotalItems()));
        job.setFaseActual(procesados < job.getTotalItems()
                ? "Leyendo documento " + (procesados + 1) + " de " + job.getTotalItems()
                : "Consolidando datos");
        jobRepository.save(job);
    }

    private void marcarConsolidacion(Long jobId) {
        ExpedienteLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setProgreso(95);
        job.setFaseActual("Consolidando datos");
        job.setMensaje("Las lecturas han terminado. Aplicando los datos seguros al expediente.");
        jobRepository.save(job);
    }

    private void finalizarJob(Long jobId) {
        ExpedienteLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        LocalDateTime fin = LocalDateTime.now();
        for (ExpedienteLecturaIaItem item : itemRepository.findByJobIdOrderById(jobId)) {
            Resultado resultado = resultado(item.getDocumento().getId(), item.getTipoLectura());
            item.setEstado(resultado.estado());
            item.setModelo(resultado.modelo());
            item.setConfianza(resultado.confianza());
            item.setMensaje(resultado.mensaje());
            item.setFechaFin(fin);
            item.setDuracionMs(item.getFechaInicio() != null ? Duration.between(item.getFechaInicio(), fin).toMillis() : null);
            itemRepository.save(item);
        }
        recalcularYFinalizar(job, fin);
    }

    private void recalcularYFinalizar(ExpedienteLecturaIaJob job, LocalDateTime fin) {
        List<ExpedienteLecturaIaItem> items = itemRepository.findByJobIdOrderById(job.getId());
        int revision = (int) items.stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.REQUIERE_REVISION).count();
        int errores = (int) items.stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.ERROR).count();
        job.setItemsProcesados(items.size());
        job.setItemsRevision(revision);
        job.setItemsError(errores);
        job.setProgreso(100);
        job.setFechaFin(fin);
        if (errores == items.size() && !items.isEmpty()) {
            job.setEstado(EstadoLecturaIaJob.ERROR);
            job.setFaseActual("Lectura fallida");
            job.setMensaje("No se pudo leer ningun documento. Revisa los errores y vuelve a intentarlo.");
        } else if (revision > 0 || errores > 0) {
            job.setEstado(EstadoLecturaIaJob.REQUIERE_REVISION);
            job.setFaseActual("Lectura completada con revisiones");
            job.setMensaje("Lectura terminada. Hay documentos que necesitan revision.");
        } else {
            job.setEstado(EstadoLecturaIaJob.COMPLETADO);
            job.setFaseActual("Lectura completada");
            job.setMensaje("Todos los documentos compatibles se han leido correctamente.");
        }
        jobRepository.save(job);
    }

    private Resultado resultado(Long documentoId, TipoLecturaIa tipo) {
        if (tipo == TipoLecturaIa.IDENTIDAD) {
            return identidadRepository.findByDocumentoId(documentoId)
                    .map(value -> resultado(value.isRequiereRevision(), value.getModelo(), value.getConfianzaGlobal(), value.getMensaje()))
                    .orElseGet(() -> error("No se obtuvo lectura de identidad."));
        }
        if (tipo == TipoLecturaIa.ROLES) {
            return rolesRepository.findByDocumentoId(documentoId)
                    .map(value -> resultado(value.isRequiereRevision(), value.getModelo(), value.getConfianzaGlobal(), value.getMensaje()))
                    .orElseGet(() -> error("No se obtuvo lectura de roles."));
        }
        return vehiculoRepository.findByDocumentoId(documentoId)
                .map(value -> resultado(value.isRequiereRevision(), value.getModelo(), value.getConfianzaGlobal(), value.getMensaje()))
                .orElseGet(() -> error("No se obtuvo lectura del vehiculo."));
    }

    private Resultado resultado(boolean revision, String modelo, Double confianza, String mensaje) {
        return new Resultado(revision ? EstadoLecturaIaItem.REQUIERE_REVISION : EstadoLecturaIaItem.COMPLETADO,
                modelo, confianza, mensaje);
    }

    private Resultado error(String mensaje) {
        return new Resultado(EstadoLecturaIaItem.ERROR, null, null, mensaje);
    }

    private void finalizarConError(Long jobId, String mensaje) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ExpedienteLecturaIaJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) return;
            LocalDateTime fin = LocalDateTime.now();
            for (ExpedienteLecturaIaItem item : job.getItems()) {
                if (item.getEstado().activo()) {
                    item.setEstado(EstadoLecturaIaItem.ERROR);
                    item.setMensaje(mensaje);
                    item.setFechaFin(fin);
                }
            }
            job.setEstado(EstadoLecturaIaJob.ERROR);
            job.setItemsProcesados(job.getTotalItems());
            job.setItemsError(job.getTotalItems());
            job.setProgreso(100);
            job.setFaseActual("Error de lectura");
            job.setMensaje(mensaje);
            job.setFechaFin(fin);
            jobRepository.save(job);
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recuperarTrabajosInterrumpidos() {
        for (ExpedienteLecturaIaJob job : jobRepository.findByEstadoInOrderByFechaCreacionAsc(ESTADOS_ACTIVOS)) {
            job.setEstado(EstadoLecturaIaJob.PENDIENTE);
            job.setFaseActual("Reanudando lectura");
            job.getItems().stream().filter(item -> item.getEstado() == EstadoLecturaIaItem.PROCESANDO).forEach(item -> {
                item.setEstado(EstadoLecturaIaItem.PENDIENTE);
                item.setMensaje("Lectura reanudada tras reinicio");
            });
            jobRepository.save(job);
            programarTrasCommit(job.getId());
        }
    }

    @PreDestroy
    void cerrarExecutor() { executor.shutdownNow(); }

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

    private String mensajeSeguro(Throwable throwable) {
        String value = throwable.getMessage();
        if (value == null || value.isBlank()) value = "Error inesperado durante la lectura.";
        return value.length() > 950 ? value.substring(0, 950) : value;
    }

    private record JobContext(Long expedienteId, Long usuarioId, boolean forzar) {}
    private record ItemContext(Long documentoId, TipoLecturaIa tipo, boolean forzar) {}
    private record Resultado(EstadoLecturaIaItem estado, String modelo, Double confianza, String mensaje) {}
}
