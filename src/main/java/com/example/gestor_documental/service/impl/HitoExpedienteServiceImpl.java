package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoOperacionExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.enums.TipoTramiteEnum;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.OperacionInvalidaException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.HitoExpediente;
import com.example.gestor_documental.model.Usuario;
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

@Service
@RequiredArgsConstructor
public class HitoExpedienteServiceImpl implements HitoExpedienteService {

    private final HitoExpedienteRepository hitoExpedienteRepository;
    private final ExpedienteRepository expedienteRepository;
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
        if (codigo == CodigoHitoExpediente.ENVIADO_DGT || codigo == CodigoHitoExpediente.COM_ENVIADO_DGT) {
            validarHitoCompletado(expedienteId, codigo == CodigoHitoExpediente.COM_ENVIADO_DGT
                            ? CodigoHitoExpediente.COM_MODELO_620_PRESENTADO
                            : CodigoHitoExpediente.MODELO_620_PRESENTADO,
                    "Primero debe marcarse el Modelo 620 como presentado.");
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
        if (codigo == CodigoHitoExpediente.BATE_FINALIZADO) {
            validarHitoCompletado(expedienteId, CodigoHitoExpediente.BATE_MODELO_620_PRESENTADO,
                    "Primero debe marcarse el Modelo 620 de la operacion BATE como presentado.");
        }
        if (codigo == CodigoHitoExpediente.COM_FINALIZADO) {
            validarHitoCompletado(expedienteId, CodigoHitoExpediente.COM_MODELO_620_PRESENTADO,
                    "Primero debe marcarse el Modelo 620 de la operacion COM como presentado.");
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

    private void validarHitoCompletado(Long expedienteId, CodigoHitoExpediente codigo, String mensaje) {
        if (!hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expedienteId, codigo)) {
            throw new OperacionInvalidaException(mensaje);
        }
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
