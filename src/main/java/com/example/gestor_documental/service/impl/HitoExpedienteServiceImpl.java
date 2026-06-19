package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoOperacionExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HitoExpediente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.DocumentoRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.OperacionExpedienteRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.HitoExpedienteService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.TipoIncidenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HitoExpedienteServiceImpl implements HitoExpedienteService {

    private static final List<CodigoHitoExpediente> HITOS_DIRECTOS = List.of(
            CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION,
            CodigoHitoExpediente.MODELO_620_PRESENTADO,
            CodigoHitoExpediente.ENVIADO_DGT
    );

    private static final List<CodigoHitoExpediente> HITOS_BATECOM = List.of(
            CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION,
            CodigoHitoExpediente.BATE_MODELO_620_PRESENTADO,
            CodigoHitoExpediente.BATE_FINALIZADO,
            CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION,
            CodigoHitoExpediente.COM_MODELO_620_PRESENTADO,
            CodigoHitoExpediente.COM_ENVIADO_DGT,
            CodigoHitoExpediente.COM_FINALIZADO
    );

    private final HitoExpedienteRepository hitoExpedienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final DocumentoRepository documentoRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final OperacionExpedienteRepository operacionRepository;
    private final ExpedienteService expedienteService;
    private final HistorialCambioService historialCambioService;
    private final IncidenciaService incidenciaService;

    @Override
    public List<HitoExpediente> listarPorExpediente(Long expedienteId) {
        return hitoExpedienteRepository.findByExpedienteId(expedienteId);
    }

    @Override
    @Transactional
    public HitoExpediente completar(Long expedienteId, CodigoHitoExpediente codigo, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        validarPermiso(expediente, usuario);
        validarPuedeAvanzar(expediente);

        if (codigo == CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION) {
            validarDocumentacionCompleta(expedienteId);
        }
        if (codigo == CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION) {
            validarDocumentacionCompletaOperacion(expedienteId, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE);
        }
        if (codigo == CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION) {
            validarDocumentacionCompletaOperacion(expedienteId, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM);
        }
        if (codigo == CodigoHitoExpediente.MODELO_620_PRESENTADO
                || codigo == CodigoHitoExpediente.BATE_MODELO_620_PRESENTADO
                || codigo == CodigoHitoExpediente.COM_MODELO_620_PRESENTADO) {
            validarHitoCompletado(expedienteId, tramitePrevio(codigo),
                    "Primero debe marcarse el tramite como subido en el programa de gestion.");
        }
        if (esCodigoCom(codigo)) {
            validarHitoCompletado(expedienteId, CodigoHitoExpediente.BATE_FINALIZADO,
                    "Primero debe finalizarse la operacion Entrega a compraventa (BATE).");
        }
        HitoExpediente hito = hitoExpedienteRepository
                .findByExpedienteIdAndCodigo(expedienteId, codigo)
                .orElseGet(() -> {
                    HitoExpediente nuevo = new HitoExpediente();
                    nuevo.setExpediente(expediente);
                    nuevo.setCodigo(codigo);
                    return nuevo;
                });

        hito.setFechaCompletado(LocalDateTime.now());
        hito.setCompletadoPor(usuario);
        hito.setNota(nota(codigo));
        HitoExpediente guardado = hitoExpedienteRepository.save(hito);

        actualizarOperacion(expedienteId, codigo);

        if ((codigo == CodigoHitoExpediente.ENVIADO_DGT || codigo == CodigoHitoExpediente.COM_ENVIADO_DGT)
                && expediente.getEstadoExpediente() != EstadoExpediente.ENVIADO_DGT) {
            expedienteService.cambiarEstado(expedienteId, EstadoExpediente.ENVIADO_DGT, usuario);
        } else if (codigo == CodigoHitoExpediente.COM_FINALIZADO) {
            expedienteService.cambiarEstado(expedienteId, EstadoExpediente.FINALIZADO, usuario);
        } else {
            historialCambioService.registrarCambioExpediente(expediente, usuario, "HITO EXPEDIENTE", nota(codigo));
        }

        return guardado;
    }

    @Override
    @Transactional
    public void retroceder(Long expedienteId, CodigoHitoExpediente codigo, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        validarPermiso(expediente, usuario);
        validarRetrocesoPermitido(expediente);

        List<CodigoHitoExpediente> orden = ordenRetroceso(codigo);
        int indice = orden.indexOf(codigo);
        if (indice < 0) {
            throw new OperacionInvalidaException("No se puede retroceder este hito.");
        }

        Set<CodigoHitoExpediente> codigosAEliminar = orden.subList(indice, orden.size())
                .stream()
                .collect(Collectors.toSet());
        List<HitoExpediente> hitosActuales = hitoExpedienteRepository.findByExpedienteId(expedienteId);
        List<HitoExpediente> hitosAEliminar = hitosActuales.stream()
                .filter(hito -> codigosAEliminar.contains(hito.getCodigo()))
                .toList();
        if (hitosAEliminar.isEmpty()) {
            throw new OperacionInvalidaException("El hito seleccionado no esta completado.");
        }

        Set<CodigoHitoExpediente> hitosRestantes = hitosActuales.stream()
                .map(HitoExpediente::getCodigo)
                .filter(hito -> !codigosAEliminar.contains(hito))
                .collect(Collectors.toSet());

        hitoExpedienteRepository.deleteAll(hitosAEliminar);
        actualizarOperacionesTrasRetroceso(expedienteId, hitosRestantes);
        registrarRetroceso(expediente, estadoTrasRetroceso(hitosRestantes), usuario,
                "Se retrocedio el expediente hasta el hito anterior a: " + etiqueta(codigo) + ".");
    }

    @Override
    @Transactional
    public void retrocederFinalizacion(Long expedienteId, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        validarPermiso(expediente, usuario);
        validarRetrocesoPermitido(expediente);
        if (expediente.getEstadoExpediente() != EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("El expediente no esta finalizado.");
        }

        Set<CodigoHitoExpediente> hitosActuales = hitoExpedienteRepository.findByExpedienteId(expedienteId).stream()
                .map(HitoExpediente::getCodigo)
                .collect(Collectors.toSet());
        registrarRetroceso(expediente, estadoTrasRetroceso(hitosActuales), usuario,
                "Se reabrio el cierre del expediente.");
    }

    @Override
    @Transactional
    public void finalizar(Long expedienteId, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        validarPermiso(expediente, usuario);
        validarPuedeAvanzar(expediente);
        if (expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() == TipoTramiteEnum.BATECOM) {
            throw new OperacionInvalidaException("Los expedientes BATECOM se finalizan cerrando la operacion COM.");
        }

        expedienteService.cambiarEstado(expedienteId, EstadoExpediente.FINALIZADO, usuario);
    }

    @Override
    @Transactional
    public void abrirIncidencia(Long expedienteId, Long tipoIncidenciaId, String observaciones, Usuario usuario) {
        Expediente expediente = expedienteRepository.findById(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        validarPermiso(expediente, usuario);
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("No se puede abrir una incidencia en un expediente finalizado");
        }

        incidenciaService.crearIncidenciaExpediente(
                expedienteId,
                tipoIncidenciaId,
                observaciones != null && !observaciones.isBlank() ? observaciones : "Incidencia abierta desde el cierre del expediente.",
                usuario
        );
    }

    private void validarPermiso(Expediente expediente, Usuario usuario) {
        if (!expedienteService.tienePermisoExpediente(expediente, usuario)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }
        if (usuario.getRolUsuario() != RolUsuario.ADMIN) {
            throw new AccesoDenegadoException("Solo el administrador puede avanzar hitos del expediente.");
        }
    }

    private void validarPuedeAvanzar(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            throw new OperacionInvalidaException("No se puede modificar un expediente finalizado");
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL
                || expediente.getEstadoExpediente() == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
            throw new OperacionInvalidaException("Hay una solicitud de informacion adicional pendiente antes de continuar.");
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            throw new OperacionInvalidaException("Hay documentacion solicitada pendiente antes de continuar.");
        }
        if (!incidenciaRepository.findByExpedienteIdAndResueltaFalse(expediente.getId()).isEmpty()) {
            throw new OperacionInvalidaException("Hay incidencias activas antes de continuar.");
        }
    }

    private void validarDocumentacionCompleta(Long expedienteId) {
        boolean hayPendientes = requisitoRepository.findByExpedienteIdOrderByIdAsc(expedienteId).stream()
                .filter(requisito -> requisito.getEstado() != EstadoRequisitoDocumental.POSTERIOR)
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO);
        if (hayPendientes) {
            throw new OperacionInvalidaException("Primero deben resolverse los requisitos documentales pendientes.");
        }
    }

    private void validarDocumentacionCompletaOperacion(Long expedienteId, TipoOperacionExpediente tipoOperacion) {
        boolean hayPendientes = requisitoRepository.findByExpedienteIdOrderByIdAsc(expedienteId).stream()
                .filter(requisito -> requisito.getEstado() != EstadoRequisitoDocumental.POSTERIOR)
                .filter(requisito -> requisito.getOperacion() != null && requisito.getOperacion().getTipo() == tipoOperacion)
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO);
        if (hayPendientes) {
            throw new OperacionInvalidaException("Primero deben resolverse los requisitos documentales de esta operacion.");
        }
    }

    private void validarHitoCompletado(Long expedienteId, CodigoHitoExpediente codigo, String mensaje) {
        if (!hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expedienteId, codigo)) {
            throw new OperacionInvalidaException(mensaje);
        }
    }

    private void validarModelo620Resuelto(Expediente expediente, TipoOperacionExpediente tipoOperacion, String mensaje) {
        if (!requiereModelo620(expediente)) {
            return;
        }
        CodigoHitoExpediente codigoModelo = switch (tipoOperacion) {
            case ENTREGA_COMPRAVENTA_BATE -> CodigoHitoExpediente.BATE_MODELO_620_PRESENTADO;
            case FINALIZACION_ENTREGA_COMPRAVENTA_COM -> CodigoHitoExpediente.COM_MODELO_620_PRESENTADO;
            default -> CodigoHitoExpediente.MODELO_620_PRESENTADO;
        };
        if (hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), codigoModelo)) {
            return;
        }
        boolean resuelto = requisitoRepository.findByExpedienteIdAndTipoDocumento(expediente.getId(), TipoDocumento.MODELO_620).stream()
                .filter(requisito -> tipoOperacion == TipoOperacionExpediente.TRASPASO_DIRECTO
                        || (requisito.getOperacion() != null && requisito.getOperacion().getTipo() == tipoOperacion))
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.APORTADO
                        || requisito.getEstado() == EstadoRequisitoDocumental.OMITIDO);
        if (!resuelto) {
            throw new OperacionInvalidaException(mensaje);
        }
    }

    private boolean requiereModelo620(Expediente expediente) {
        TipoTramiteEnum tramite = expediente.getTipoTramite() != null ? expediente.getTipoTramite().getNombre() : null;
        return tramite != TipoTramiteEnum.NOTIFICACION_VENTA;
    }

    private CodigoHitoExpediente tramitePrevio(CodigoHitoExpediente codigo) {
        return switch (codigo) {
            case BATE_MODELO_620_PRESENTADO -> CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION;
            case COM_MODELO_620_PRESENTADO -> CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION;
            default -> CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION;
        };
    }

    private boolean esCodigoCom(CodigoHitoExpediente codigo) {
        return codigo == CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION
                || codigo == CodigoHitoExpediente.COM_MODELO_620_PRESENTADO
                || codigo == CodigoHitoExpediente.COM_ENVIADO_DGT
                || codigo == CodigoHitoExpediente.COM_FINALIZADO;
    }

    private void actualizarOperacion(Long expedienteId, CodigoHitoExpediente codigo) {
        if (codigo == CodigoHitoExpediente.BATE_FINALIZADO) {
            operacionRepository.findByExpedienteIdAndTipo(expedienteId, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE)
                    .ifPresent(operacion -> {
                        operacion.setEstado(EstadoOperacionExpediente.FINALIZADA);
                        operacionRepository.save(operacion);
                    });
            operacionRepository.findByExpedienteIdAndTipo(expedienteId, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM)
                    .ifPresent(operacion -> {
                        operacion.setEstado(EstadoOperacionExpediente.EN_CURSO);
                        operacionRepository.save(operacion);
                    });
        }
        if (codigo == CodigoHitoExpediente.COM_FINALIZADO) {
            operacionRepository.findByExpedienteIdAndTipo(expedienteId, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM)
                    .ifPresent(operacion -> {
                        operacion.setEstado(EstadoOperacionExpediente.FINALIZADA);
                        operacionRepository.save(operacion);
            });
        }
    }

    private List<CodigoHitoExpediente> ordenRetroceso(CodigoHitoExpediente codigo) {
        if (HITOS_DIRECTOS.contains(codigo)) {
            return HITOS_DIRECTOS;
        }
        if (HITOS_BATECOM.contains(codigo)) {
            return HITOS_BATECOM;
        }
        return List.of();
    }

    private void validarRetrocesoPermitido(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO && tieneJustificantesFinales(expediente)) {
            throw new OperacionInvalidaException("No se puede retroceder un expediente finalizado con todos los justificantes finales.");
        }
    }

    private boolean tieneJustificantesFinales(Expediente expediente) {
        List<Documento> documentos = documentoRepository.findByExpedienteId(expediente.getId());
        List<com.example.gestor_documental.model.RequisitoDocumentalExpediente> requisitosFinales = requisitoRepository
                .findByExpedienteIdOrderByIdAsc(expediente.getId())
                .stream()
                .filter(requisito -> requisito.getTipoDocumento() == TipoDocumento.MODELO_620
                        || requisito.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT
                        || requisito.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE)
                .toList();

        if (!requisitosFinales.isEmpty()) {
            return requisitosFinales.stream()
                    .allMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.APORTADO
                            || requisito.getEstado() == EstadoRequisitoDocumental.OMITIDO);
        }

        boolean tieneJustificanteDgt = documentos.stream().anyMatch(this::esJustificanteDgt);
        boolean tieneModelo620 = documentos.stream().anyMatch(documento -> documento.getTipoDocumento() == TipoDocumento.MODELO_620);
        return tieneJustificanteDgt && (!requiereModelo620(expediente) || tieneModelo620);
    }

    private boolean esJustificanteDgt(Documento documento) {
        return documento.getTipoDocumento() == TipoDocumento.HUELLA_TRAMITE
                || documento.getTipoDocumento() == TipoDocumento.COMPROBANTE_DGT;
    }

    private void actualizarOperacionesTrasRetroceso(Long expedienteId, Set<CodigoHitoExpediente> hitosRestantes) {
        operacionRepository.findByExpedienteIdAndTipo(expedienteId, TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE)
                .ifPresent(operacion -> {
                    operacion.setEstado(hitosRestantes.contains(CodigoHitoExpediente.BATE_FINALIZADO)
                            ? EstadoOperacionExpediente.FINALIZADA
                            : EstadoOperacionExpediente.EN_CURSO);
                    operacionRepository.save(operacion);
                });
        operacionRepository.findByExpedienteIdAndTipo(expedienteId, TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM)
                .ifPresent(operacion -> {
                    if (!hitosRestantes.contains(CodigoHitoExpediente.BATE_FINALIZADO)) {
                        operacion.setEstado(EstadoOperacionExpediente.PENDIENTE);
                    } else if (hitosRestantes.contains(CodigoHitoExpediente.COM_FINALIZADO)) {
                        operacion.setEstado(EstadoOperacionExpediente.FINALIZADA);
                    } else {
                        operacion.setEstado(EstadoOperacionExpediente.EN_CURSO);
                    }
                    operacionRepository.save(operacion);
                });
    }

    private EstadoExpediente estadoTrasRetroceso(Set<CodigoHitoExpediente> hitosRestantes) {
        if (hitosRestantes.contains(CodigoHitoExpediente.ENVIADO_DGT)
                || hitosRestantes.contains(CodigoHitoExpediente.COM_ENVIADO_DGT)) {
            return EstadoExpediente.ENVIADO_DGT;
        }
        return EstadoExpediente.EN_TRAMITE;
    }

    private void registrarRetroceso(Expediente expediente, EstadoExpediente nuevoEstado, Usuario usuario, String descripcion) {
        EstadoExpediente estadoAnterior = expediente.getEstadoExpediente();
        expediente.setEstadoExpediente(nuevoEstado);
        expediente.setEstadoPrevioPausa(null);
        expediente.setFechaUltimaModificacion(LocalDateTime.now());
        expediente.setModificadoPor(usuario);
        expedienteRepository.save(expediente);
        historialCambioService.registrarCambioExpediente(
                expediente,
                usuario,
                "RETROCESO FASE",
                descripcion + " Estado anterior: " + estadoAnterior.name() + ". Estado nuevo: " + nuevoEstado.name()
        );
    }

    private String etiqueta(CodigoHitoExpediente codigo) {
        return switch (codigo) {
            case TRAMITE_PROGRAMA_GESTION -> "tramite subido al programa de gestion";
            case MODELO_620_PRESENTADO -> "Modelo 620 presentado";
            case ENVIADO_DGT -> "envio a DGT";
            case BATE_TRAMITE_PROGRAMA_GESTION -> "BATE subido al programa de gestion";
            case BATE_MODELO_620_PRESENTADO -> "Modelo 620 presentado en BATE";
            case BATE_FINALIZADO -> "BATE finalizado";
            case COM_TRAMITE_PROGRAMA_GESTION -> "COM subido al programa de gestion";
            case COM_MODELO_620_PRESENTADO -> "Modelo 620 presentado en COM";
            case COM_ENVIADO_DGT -> "COM enviado a DGT";
            case COM_FINALIZADO -> "COM finalizado";
        };
    }

    private String nota(CodigoHitoExpediente codigo) {
        return switch (codigo) {
            case TRAMITE_PROGRAMA_GESTION -> "Tramite subido en el programa de gestion.";
            case MODELO_620_PRESENTADO -> "Modelo 620 presentado.";
            case ENVIADO_DGT -> "Expediente enviado a DGT.";
            case BATE_TRAMITE_PROGRAMA_GESTION -> "BATE subido en el programa de gestion.";
            case BATE_MODELO_620_PRESENTADO -> "Modelo 620 presentado en BATE.";
            case BATE_FINALIZADO -> "Entrega a compraventa (BATE) finalizada.";
            case COM_TRAMITE_PROGRAMA_GESTION -> "COM subido en el programa de gestion.";
            case COM_MODELO_620_PRESENTADO -> "Modelo 620 presentado en COM.";
            case COM_ENVIADO_DGT -> "COM enviado a DGT.";
            case COM_FINALIZADO -> "Finalizacion entrega a compraventa (COM) finalizada.";
        };
    }
}
