package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.dto.expediente.ClienteResumenResponse;
import com.example.gestor_documental.dto.expediente.DocumentoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteDetailResponse;
import com.example.gestor_documental.dto.expediente.ExpedienteVinculadoResponse;
import com.example.gestor_documental.dto.expediente.HistorialExpedienteResponse;
import com.example.gestor_documental.dto.expediente.HitoAccionResponse;
import com.example.gestor_documental.dto.expediente.HitoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InconsistenciaDocumentalResponse;
import com.example.gestor_documental.dto.expediente.IncidenciaExpedienteResponse;
import com.example.gestor_documental.dto.expediente.InteresadoExpedienteResponse;
import com.example.gestor_documental.dto.expediente.MensajeExpedienteResponse;
import com.example.gestor_documental.dto.expediente.OperacionExpedienteResponse;
import com.example.gestor_documental.dto.expediente.RequisitoDocumentalResponse;
import com.example.gestor_documental.dto.expediente.UsuarioResumenResponse;
import com.example.gestor_documental.dto.expediente.WhatsappExpedienteResponse;
import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.RolInteresado;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.enums.TipoIncidenciaEnum;
import com.example.gestor_documental.enums.TipoLogoCliente;
import com.example.gestor_documental.enums.TipoOperacionExpediente;
import com.example.gestor_documental.exception.AccesoDenegadoException;
import com.example.gestor_documental.exception.RecursoNoEncontradoException;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.DocumentoIdentidadLectura;
import com.example.gestor_documental.model.DocumentoRolesLectura;
import com.example.gestor_documental.model.DocumentoVehiculoLectura;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.ExpedienteInteresado;
import com.example.gestor_documental.model.HistorialCambio;
import com.example.gestor_documental.model.HitoExpediente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Mensaje;
import com.example.gestor_documental.model.OperacionExpediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.repository.DocumentoIdentidadLecturaRepository;
import com.example.gestor_documental.repository.DocumentoRolesLecturaRepository;
import com.example.gestor_documental.repository.DocumentoVehiculoLecturaRepository;
import com.example.gestor_documental.repository.ExpedienteInteresadoRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.ExpedienteDetalleApiService;
import com.example.gestor_documental.service.ExpedienteService;
import com.example.gestor_documental.service.ExpedienteTipoTramitePolicyService;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.HitoExpedienteService;
import com.example.gestor_documental.service.IncidenciaService;
import com.example.gestor_documental.service.MensajeService;
import com.example.gestor_documental.service.OperacionExpedienteService;
import com.example.gestor_documental.service.RequisitoDocumentalExpedienteService;
import com.example.gestor_documental.util.ClienteBrandingUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpedienteDetalleApiServiceImpl implements ExpedienteDetalleApiService {

    private static final Set<TipoDocumento> DOCUMENTOS_BASE_REQUERIDOS = EnumSet.of(
            TipoDocumento.DNI,
            TipoDocumento.CONTRATO_COMPRAVENTA,
            TipoDocumento.PERMISO_CIRCULACION,
            TipoDocumento.FICHA_TECNICA,
            TipoDocumento.MANDATO
    );

    private final ExpedienteService expedienteService;
    private final DocumentoService documentoService;
    private final IncidenciaService incidenciaService;
    private final HistorialCambioService historialCambioService;
    private final MensajeService mensajeService;
    private final OperacionExpedienteService operacionExpedienteService;
    private final RequisitoDocumentalExpedienteService requisitoDocumentalService;
    private final HitoExpedienteService hitoExpedienteService;
    private final ExpedienteTipoTramitePolicyService tipoTramitePolicyService;
    private final ExpedienteInteresadoRepository expedienteInteresadoRepository;
    private final DocumentoIdentidadLecturaRepository documentoIdentidadLecturaRepository;
    private final DocumentoRolesLecturaRepository documentoRolesLecturaRepository;
    private final DocumentoVehiculoLecturaRepository documentoVehiculoLecturaRepository;
    private final WhatsappWebhookEventoRepository whatsappWebhookEventoRepository;

    @Override
    @Transactional
    public ExpedienteDetailResponse obtenerDetalle(Long expedienteId, Usuario usuarioLogueado) {
        Expediente expediente = expedienteService.buscarPorId(expedienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente no encontrado"));

        if (!expedienteService.tienePermisoExpediente(expediente, usuarioLogueado)) {
            throw new AccesoDenegadoException("No tienes permiso para acceder a este expediente");
        }

        List<Documento> documentos = documentoService.listarPorExpediente(expedienteId);
        List<ExpedienteInteresado> interesados = expedienteInteresadoRepository.findByExpedienteId(expedienteId);
        List<Incidencia> incidencias = incidenciaService.listarPorExpediente(expedienteId);
        List<HistorialCambio> historial = historialCambioService.listarPorExpediente(expedienteId);
        List<Mensaje> mensajes = mensajeService.listarPorExpediente(expedienteId);
        List<WhatsappWebhookEvento> whatsappMensajes = whatsappWebhookEventoRepository.findMensajesClienteByExpedienteId(expedienteId);
        List<OperacionExpediente> operaciones = operacionExpedienteService.sincronizarYListar(expediente);
        List<RequisitoDocumentalExpediente> requisitos = requisitoDocumentalService.sincronizarYListar(expediente, interesados, documentos, usuarioLogueado);
        Map<CodigoHitoExpediente, HitoExpediente> hitosPersistidos = hitoExpedienteService.listarPorExpediente(expedienteId)
                .stream()
                .collect(Collectors.toMap(HitoExpediente::getCodigo, hito -> hito, (actual, repetido) -> actual));

        EstadoExpediente estadoOperativo = calcularEstadoOperativo(expediente, incidencias);
        EstadoDetalle estadoDetalle = calcularEstadoDetalle(expediente, estadoOperativo, documentos, requisitos, hitosPersistidos);
        List<HitoExpedienteResponse> hitos = calcularHitos(expediente, estadoDetalle);
        List<OperacionExpedienteResponse> operacionesResponse = operaciones.stream()
                .map(operacion -> mapOperacion(expediente, operacion, estadoDetalle))
                .toList();

        return ExpedienteDetailResponse.builder()
                .id(expediente.getId())
                .referencia("EXP-" + expediente.getId())
                .matricula(expediente.getMatricula())
                .tipoTramite(expediente.getTipoTramite() != null && expediente.getTipoTramite().getNombre() != null
                        ? expediente.getTipoTramite().getNombre().name()
                        : null)
                .tipoTramiteDescripcion(expediente.getTipoTramite() != null ? expediente.getTipoTramite().getDescripcion() : null)
                .estado(estadoOperativo != null ? estadoOperativo.name() : null)
                .faseActual(calcularFaseActual(estadoOperativo, estadoDetalle))
                .fechaInicio(formatearFecha(expediente.getFechaCreacion()))
                .fechaUltimaModificacion(formatearFecha(expediente.getFechaUltimaModificacion()))
                .observaciones(expediente.getObservaciones())
                .solicitudId(expediente.getSolicitud() != null ? expediente.getSolicitud().getId() : null)
                .tramiteVinculado(mapTramiteVinculado(expediente))
                .siguientePaso(calcularSiguientePaso(estadoDetalle, hitos))
                .mensajesNoLeidos((int) mensajeService.contarNoLeidosExpediente(expedienteId, usuarioLogueado))
                .cliente(mapCliente(expediente.getCliente()))
                .creadoPor(mapUsuario(expediente.getCreadoPor()))
                .modificadoPor(mapUsuario(expediente.getModificadoPor()))
                .interesados(interesados.stream().map(this::mapInteresado).toList())
                .documentos(mapDocumentos(documentos, estadoDetalle))
                .requisitosDocumentales(requisitos.stream().map(this::mapRequisitoDocumental).toList())
                .inconsistenciasDocumentales(calcularInconsistenciasDocumentales(requisitos, documentos, interesados))
                .operaciones(operacionesResponse)
                .hitos(hitos)
                .incidencias(incidencias.stream().map(incidencia -> mapIncidencia(incidencia, documentos, expediente)).toList())
                .historial(historial.stream().map(this::mapHistorial).toList())
                .mensajes(mensajes.stream().map(mensaje -> mapMensaje(mensaje, usuarioLogueado)).toList())
                .whatsappMensajes(whatsappMensajes.stream().map(this::mapWhatsapp).toList())
                .build();
    }

    private WhatsappExpedienteResponse mapWhatsapp(WhatsappWebhookEvento evento) {
        return WhatsappExpedienteResponse.builder()
                .id(evento.getId())
                .telefono(evento.getTelefono())
                .nombrePerfil(evento.getNombrePerfil())
                .tipo(evento.getTipo())
                .texto(evento.getTexto())
                .estado(evento.getEstado() != null ? evento.getEstado().name() : null)
                .fechaRecepcion(formatearFecha(evento.getFechaRecepcion()))
                .fechaRevision(formatearFecha(evento.getFechaRevision()))
                .revisadoPor(evento.getRevisadoPor() != null ? nombreCompleto(evento.getRevisadoPor()) : null)
                .build();
    }

    private EstadoDetalle calcularEstadoDetalle(
            Expediente expediente,
            EstadoExpediente estadoOperativo,
            List<Documento> documentos,
            List<RequisitoDocumentalExpediente> requisitos,
            Map<CodigoHitoExpediente, HitoExpediente> hitosPersistidos
    ) {
        Set<TipoDocumento> tiposSubidos = documentos.stream()
                .map(Documento::getTipoDocumento)
                .collect(Collectors.toSet());
        boolean documentacionBaseCompleta = requisitos.stream()
                .filter(requisito -> requisito.getEstado() != EstadoRequisitoDocumental.POSTERIOR)
                .filter(requisito -> !esDocumentoFinal(requisito.getTipoDocumento()))
                .noneMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO);
        boolean expedienteCompletoSubido = tiposSubidos.contains(TipoDocumento.EXPEDIENTE_COMPLETO);
        boolean finalizado = estadoOperativo == EstadoExpediente.FINALIZADO;
        boolean cancelado = estadoOperativo == EstadoExpediente.CANCELADO;
        boolean conIncidencia = estadoOperativo == EstadoExpediente.INCIDENCIA
                || estadoOperativo == EstadoExpediente.REVISANDO_INCIDENCIAS;
        boolean documentacionSolicitada = estadoOperativo == EstadoExpediente.PENDIENTE_DOCUMENTACION
                || estadoOperativo == EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO;
        boolean informacionSolicitada = estadoOperativo == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL;
        boolean informacionRecibida = estadoOperativo == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA;
        boolean requisitosInicialesPendientes = !documentacionBaseCompleta;
        boolean modelo620Subido = !requiereModelo620(expediente)
                || tiposSubidos.contains(TipoDocumento.MODELO_620)
                || hitosPersistidos.containsKey(CodigoHitoExpediente.MODELO_620_PRESENTADO)
                || requisitoModeloResuelto(requisitos, null);
        boolean tramiteSubido = finalizado || hitosPersistidos.containsKey(CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION);
        boolean enviadoDgt = finalizado
                || estadoOperativo == EstadoExpediente.ENVIADO_DGT
                || hitosPersistidos.containsKey(CodigoHitoExpediente.ENVIADO_DGT);
        return new EstadoDetalle(tiposSubidos, documentacionBaseCompleta, expedienteCompletoSubido, modelo620Subido,
                requisitosInicialesPendientes, finalizado, cancelado, conIncidencia, documentacionSolicitada, informacionSolicitada, informacionRecibida,
                tramiteSubido, enviadoDgt, hitosPersistidos, requisitos);
    }

    private EstadoExpediente calcularEstadoOperativo(Expediente expediente, List<Incidencia> incidencias) {
        EstadoExpediente estado = expediente.getEstadoExpediente();
        if (estado == EstadoExpediente.FINALIZADO
                || estado == EstadoExpediente.CANCELADO
                || estado == EstadoExpediente.RECHAZADO
                || estado == EstadoExpediente.PENDIENTE_DOCUMENTACION
                || estado == EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO
                || estado == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL
                || estado == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA
                || estado == EstadoExpediente.INCIDENCIA
                || estado == EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return estado;
        }
        List<Incidencia> activas = incidencias.stream()
                .filter(incidencia -> !incidencia.isResuelta())
                .toList();
        if (activas.isEmpty()) {
            return estado;
        }
        boolean documentacionPendiente = activas.stream()
                .anyMatch(incidencia -> incidencia.getTipoIncidencia() != null
                        && incidencia.getTipoIncidencia().getNombre() == TipoIncidenciaEnum.PENDIENTE_DOCUMENTACION);
        return documentacionPendiente ? EstadoExpediente.PENDIENTE_DOCUMENTACION : EstadoExpediente.INCIDENCIA;
    }

    private String calcularFaseActual(EstadoExpediente estadoOperativo, EstadoDetalle estadoDetalle) {
        if (estadoOperativo == EstadoExpediente.FINALIZADO) {
            return "Finalizado";
        }
        if (estadoOperativo == EstadoExpediente.CANCELADO) {
            return "Cancelado por el cliente";
        }
        if (estadoOperativo == EstadoExpediente.INCIDENCIA
                || estadoOperativo == EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return "Incidencias";
        }
        if (estadoOperativo == EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            return "Pendiente de documentacion";
        }
        if (estadoOperativo == EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO) {
            return "Pendiente de tramite vinculado";
        }
        if (estadoOperativo == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
            return "Solicitada informacion adicional";
        }
        if (estadoOperativo == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
            return "Informacion adicional recibida";
        }
        if (!estadoDetalle.documentacionBaseCompleta()) {
            return estadoDetalle.expedienteCompletoSubido()
                    ? "Comprobacion de documentacion del expediente completo"
                    : "Comprobacion de documentacion";
        }
        if (!estadoDetalle.tramiteSubido()) {
            return "Pendiente de subir a programa de gestion";
        }
        if (!estadoDetalle.enviadoDgt()) {
            return "Tramite subido, pendiente de enviar a DGT";
        }
        return "Tramite enviado a DGT, pendiente de cierre";
    }

    private List<HitoExpedienteResponse> calcularHitos(Expediente expediente, EstadoDetalle estadoDetalle) {
        List<HitoExpedienteResponse> hitos = new ArrayList<>();
        boolean finalizado = estadoDetalle.finalizado();
        boolean documentacionLista = finalizado || estadoDetalle.documentacionBaseCompleta();
        boolean requiereModelo620 = requiereModelo620(expediente);
        boolean modelo620Completado = finalizado || estadoDetalle.modelo620Subido();
        boolean tramiteSubido = finalizado || estadoDetalle.tramiteSubido();
        boolean enviadoDgt = finalizado || estadoDetalle.enviadoDgt();
        boolean puedeContinuarTrasTramite = finalizado || tramiteSubido || estadoDetalle.conIncidencia();
        HitoExpediente tramitePersistido = estadoDetalle.hitosPersistidos().get(CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION);
        HitoExpediente modelo620Persistido = estadoDetalle.hitosPersistidos().get(CodigoHitoExpediente.MODELO_620_PRESENTADO);
        HitoExpediente enviadoDgtPersistido = estadoDetalle.hitosPersistidos().get(CodigoHitoExpediente.ENVIADO_DGT);

        hitos.add(HitoExpedienteResponse.builder()
                .id("solicitud-revisada")
                .titulo("Tramite creado")
                .descripcion("Se completa automaticamente al crear el expediente desde una solicitud validada o al crear el expediente manualmente.")
                .estado("COMPLETADO")
                .tipo("AUTOMATICO")
                .fecha(formatearFecha(expediente.getFechaCreacion()))
                .usuario(expediente.getCreadoPor() != null ? nombreCompleto(expediente.getCreadoPor()) : "Sistema")
                .completado(true)
                .bloqueado(false)
                .build());

        hitos.add(HitoExpedienteResponse.builder()
                .id("documentacion-completa")
                .titulo(documentacionLista ? "Documentacion comprobada" : "Comprobacion de documentacion")
                .descripcion("Se completa cuando todos los documentos base requeridos estan subidos o el expediente ya esta cerrado.")
                .estado(documentacionLista ? "COMPLETADO" : "ACTUAL")
                .tipo("AUTOMATICO")
                .nota(calcularNotaDocumentacion(estadoDetalle))
                .completado(documentacionLista)
                .bloqueado(false)
                .build());

        hitos.add(HitoExpedienteResponse.builder()
                .id("tramite-programa-gestion")
                .titulo(tramiteSubido ? "Tramite subido al programa de gestion" : "Pendiente de subir a programa de gestion")
                .descripcion("Confirmacion de que el expediente ya se ha cargado en el programa de gestion.")
                .estado(tramiteSubido ? "COMPLETADO" : documentacionLista ? "ACTUAL" : "BLOQUEADO")
                .tipo("MANUAL")
                .fecha(fechaHito(tramitePersistido, finalizado ? expediente.getFechaUltimaModificacion() : null))
                .usuario(usuarioHito(tramitePersistido))
                .nota(tramiteSubido ? "Tramite cargado en el programa de gestion"
                        : documentacionLista ? "Pendiente de confirmacion manual"
                        : "Primero debe revisarse la documentacion base")
                .accion(!tramiteSubido && documentacionLista ? "COMPLETAR_HITO" : null)
                .accionLabel(!tramiteSubido && documentacionLista ? "Marcar subido" : null)
                .acciones(List.of())
                .completado(tramiteSubido)
                .bloqueado(!tramiteSubido && !documentacionLista)
                .build());

        hitos.add(HitoExpedienteResponse.builder()
                .id("modelo-620-presentado")
                .titulo(!requiereModelo620
                        ? "Modelo 620 no requerido"
                        : modelo620Completado
                        ? "Impuesto 620 presentado"
                        : tramiteSubido
                                ? "Tramite subido, pendiente de pasar el impuesto 620"
                                : "Pendiente de pasar el impuesto 620")
                .descripcion(!requiereModelo620
                        ? "Este tipo de tramite no requiere Modelo 620."
                        : "Documento de fase posterior, despues de subir el tramite en el programa de gestion.")
                .estado(modelo620Completado ? "COMPLETADO" : tramiteSubido ? "ACTUAL" : "BLOQUEADO")
                .tipo("MANUAL")
                .fecha(fechaHito(modelo620Persistido, null))
                .usuario(usuarioHito(modelo620Persistido))
                .nota(!requiereModelo620
                        ? "No aplica para este tramite"
                        : modelo620Completado ? "Modelo 620 presentado" : tramiteSubido ? "Pendiente de confirmacion manual" : "Primero debe subirse el tramite al programa de gestion")
                .accion(requiereModelo620 && !modelo620Completado && tramiteSubido ? "COMPLETAR_HITO" : null)
                .accionLabel(requiereModelo620 && !modelo620Completado && tramiteSubido ? "Marcar presentado" : null)
                .acciones(!modelo620Completado && tramiteSubido
                        ? accionesConRetroceso(requiereModelo620 ? "Marcar presentado" : null,
                                requiereModelo620 ? CodigoHitoExpediente.MODELO_620_PRESENTADO : null,
                                CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION)
                        : List.of())
                .completado(modelo620Completado)
                .bloqueado(!modelo620Completado && !tramiteSubido)
                .build());

        List<HitoAccionResponse> accionesCierre = accionesCierre(finalizado, estadoDetalle.conIncidencia(), puedeContinuarTrasTramite, enviadoDgt);
        if (finalizado) {
            accionesCierre = new ArrayList<>(accionesCierre);
            accionesCierre.add(accionRetrocesoFinalizacion());
        } else if (enviadoDgtPersistido != null) {
            accionesCierre = new ArrayList<>(accionesCierre);
            accionesCierre.add(accionRetroceso(CodigoHitoExpediente.ENVIADO_DGT, "Retroceder"));
        } else if (puedeContinuarTrasTramite) {
            accionesCierre = new ArrayList<>(accionesCierre);
            accionesCierre.add(accionRetroceso(
                    modelo620Persistido != null ? CodigoHitoExpediente.MODELO_620_PRESENTADO : CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION,
                    "Retroceder"));
        }
        hitos.add(HitoExpedienteResponse.builder()
                .id("finalizado-incidencia")
                .titulo(tituloCierre(finalizado, estadoDetalle.conIncidencia(), enviadoDgt))
                .descripcion("Decision final del expediente: enviar a DGT, abrir incidencia o finalizar.")
                .estado(finalizado ? "COMPLETADO" : estadoDetalle.conIncidencia() ? "ACTUAL" : puedeContinuarTrasTramite ? "ACTUAL" : "BLOQUEADO")
                .tipo("DECISION")
                .fecha(fechaHito(enviadoDgtPersistido, expediente.getEstadoExpediente() == EstadoExpediente.ENVIADO_DGT ? expediente.getFechaUltimaModificacion() : null))
                .usuario(usuarioHito(enviadoDgtPersistido))
                .nota(notaCierre(finalizado, estadoDetalle.conIncidencia(), enviadoDgt, puedeContinuarTrasTramite))
                .acciones(accionesCierre)
                .accion(accionesCierre.isEmpty() ? null : accionesCierre.get(0).getTipo())
                .accionLabel(accionesCierre.isEmpty() ? null : accionesCierre.get(0).getLabel())
                .completado(finalizado)
                .bloqueado(!finalizado && !puedeContinuarTrasTramite && !estadoDetalle.conIncidencia())
                .build());

        return hitos;
    }

    private OperacionExpedienteResponse mapOperacion(Expediente expediente, OperacionExpediente operacion, EstadoDetalle estadoDetalle) {
        boolean bateFinalizado = estadoDetalle.hitosPersistidos().containsKey(CodigoHitoExpediente.BATE_FINALIZADO);
        boolean operacionCom = operacion.getTipo() == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM;
        boolean bloqueada = operacionCom && !bateFinalizado;
        return OperacionExpedienteResponse.builder()
                .id(operacion.getId())
                .tipo(operacion.getTipo() != null ? operacion.getTipo().name() : null)
                .label(operacion.getTipo() != null ? operacion.getTipo().getLabel() : null)
                .estado(bloqueada ? "BLOQUEADA" : operacion.getEstado().name())
                .orden(operacion.getOrden())
                .descripcion(operacion.getDescripcion())
                .bloqueada(bloqueada)
                .motivoBloqueo(bloqueada ? "Primero debe finalizarse Entrega a compraventa (BATE)." : null)
                .hitos(calcularHitosOperacion(expediente, operacion.getTipo(), estadoDetalle, bloqueada))
                .build();
    }

    private List<HitoExpedienteResponse> calcularHitosOperacion(
            Expediente expediente,
            TipoOperacionExpediente tipoOperacion,
            EstadoDetalle estadoDetalle,
            boolean bloqueada
    ) {
        if (tipoOperacion == TipoOperacionExpediente.TRASPASO_DIRECTO) {
            return calcularHitos(expediente, estadoDetalle);
        }

        CodigoHitoExpediente tramiteCodigo = tipoOperacion == TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE
                ? CodigoHitoExpediente.BATE_TRAMITE_PROGRAMA_GESTION
                : CodigoHitoExpediente.COM_TRAMITE_PROGRAMA_GESTION;
        CodigoHitoExpediente modeloCodigo = tipoOperacion == TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE
                ? CodigoHitoExpediente.BATE_MODELO_620_PRESENTADO
                : CodigoHitoExpediente.COM_MODELO_620_PRESENTADO;
        CodigoHitoExpediente cierreCodigo = tipoOperacion == TipoOperacionExpediente.ENTREGA_COMPRAVENTA_BATE
                ? CodigoHitoExpediente.BATE_FINALIZADO
                : CodigoHitoExpediente.COM_FINALIZADO;
        CodigoHitoExpediente envioCodigo = tipoOperacion == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM
                ? CodigoHitoExpediente.COM_ENVIADO_DGT
                : null;

        boolean tramite = estadoDetalle.hitosPersistidos().containsKey(tramiteCodigo);
        boolean modelo = estadoDetalle.hitosPersistidos().containsKey(modeloCodigo);
        boolean cierre = estadoDetalle.hitosPersistidos().containsKey(cierreCodigo);
        boolean envio = envioCodigo != null && estadoDetalle.hitosPersistidos().containsKey(envioCodigo);
        boolean documentacionPendiente = tieneRequisitosOperacionPendientes(estadoDetalle.requisitos(), tipoOperacion);
        boolean tramiteBloqueado = bloqueada || documentacionPendiente;
        HitoExpediente tramitePersistido = estadoDetalle.hitosPersistidos().get(tramiteCodigo);
        HitoExpediente modeloPersistido = estadoDetalle.hitosPersistidos().get(modeloCodigo);
        HitoExpediente cierrePersistido = estadoDetalle.hitosPersistidos().get(cierreCodigo);
        HitoExpediente envioPersistido = envioCodigo != null ? estadoDetalle.hitosPersistidos().get(envioCodigo) : null;
        CodigoHitoExpediente retrocesoTramite = tipoOperacion == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM
                ? CodigoHitoExpediente.BATE_FINALIZADO
                : null;

        List<HitoExpedienteResponse> hitos = new ArrayList<>();
        hitos.add(HitoExpedienteResponse.builder()
                .id(tipoOperacion.name().toLowerCase() + "-documentacion")
                .titulo("Documentacion revisada")
                .descripcion("Documentacion de la operacion preparada para gestion.")
                .estado(bloqueada ? "BLOQUEADO" : documentacionPendiente ? "ACTUAL" : "COMPLETADO")
                .tipo("AUTOMATICO")
                .completado(!tramiteBloqueado)
                .bloqueado(bloqueada)
                .nota(bloqueada
                        ? "Operacion pendiente de la fase anterior"
                        : documentacionPendiente ? "Quedan requisitos documentales pendientes de esta operacion" : "Documentacion disponible en el expediente")
                .build());
        hitos.add(HitoExpedienteResponse.builder()
                .id(tipoOperacion.name().toLowerCase() + "-tramite")
                .titulo(tramite ? "Tramite subido al programa de gestion" : "Pendiente de subir a programa de gestion")
                .descripcion("Confirmacion de carga de esta operacion en el programa de gestion.")
                .estado(tramite ? "COMPLETADO" : tramiteBloqueado ? "BLOQUEADO" : "ACTUAL")
                .tipo("MANUAL")
                .fecha(fechaHito(tramitePersistido, null))
                .usuario(usuarioHito(tramitePersistido))
                .nota(tramite
                        ? "Tramite cargado"
                        : bloqueada ? "Primero finaliza BATE"
                        : documentacionPendiente ? "Primero revisa la documentacion de esta operacion" : "Pendiente de confirmacion manual")
                .accion(!tramite && !tramiteBloqueado ? "COMPLETAR_HITO" : null)
                .accionLabel(!tramite && !tramiteBloqueado ? "Marcar subido" : null)
                .acciones(accionesHitoOperacion(tramite, !tramite && !tramiteBloqueado, tramiteCodigo, "Marcar subido", retrocesoTramite))
                .completado(tramite)
                .bloqueado(!tramite && tramiteBloqueado)
                .build());
        hitos.add(HitoExpedienteResponse.builder()
                .id(tipoOperacion.name().toLowerCase() + "-modelo-620")
                .titulo(modelo
                        ? "Impuesto 620 presentado"
                        : tramite
                                ? "Tramite subido, pendiente de pasar el impuesto 620"
                                : "Pendiente de pasar el impuesto 620")
                .descripcion("Documento fiscal posterior a la carga en programa.")
                .estado(modelo ? "COMPLETADO" : tramite ? "ACTUAL" : "BLOQUEADO")
                .tipo("MANUAL")
                .fecha(fechaHito(modeloPersistido, null))
                .usuario(usuarioHito(modeloPersistido))
                .nota(modelo ? "Modelo 620 presentado" : tramite ? "Pendiente de confirmacion manual" : "Primero debe subirse el tramite")
                .accion(!modelo && tramite ? "COMPLETAR_HITO" : null)
                .accionLabel(!modelo && tramite ? "Marcar presentado" : null)
                .acciones(accionesHitoOperacion(modelo, !modelo && tramite, modeloCodigo, "Marcar presentado", tramiteCodigo))
                .completado(modelo)
                .bloqueado(!modelo && !tramite)
                .build());
        List<HitoAccionResponse> accionesCierre = new ArrayList<>();
        if (tipoOperacion == TipoOperacionExpediente.FINALIZACION_ENTREGA_COMPRAVENTA_COM && tramite && !envio) {
            accionesCierre.add(HitoAccionResponse.builder()
                    .tipo("COMPLETAR_HITO")
                    .codigoHito(envioCodigo.name())
                    .label("Enviar a DGT")
                    .tono("primary")
                    .build());
        }
        if (tramite && !cierre) {
            accionesCierre.add(HitoAccionResponse.builder()
                    .tipo("COMPLETAR_HITO")
                    .codigoHito(cierreCodigo.name())
                    .label("Finalizar operacion")
                    .tono("success")
                    .build());
        }
        if (!cierre && tramite) {
            accionesCierre.add(accionRetroceso(envio && envioCodigo != null ? envioCodigo : modelo ? modeloCodigo : tramiteCodigo, "Retroceder"));
        }
        hitos.add(HitoExpedienteResponse.builder()
                .id(tipoOperacion.name().toLowerCase() + "-cierre")
                .titulo(cierre ? "Operacion finalizada" : "Cierre de operacion")
                .descripcion("Decision final de esta operacion.")
                .estado(cierre ? "COMPLETADO" : tramite ? "ACTUAL" : "BLOQUEADO")
                .tipo("DECISION")
                .fecha(fechaHito(cierrePersistido != null ? cierrePersistido : envioPersistido, null))
                .usuario(usuarioHito(cierrePersistido != null ? cierrePersistido : envioPersistido))
                .nota(cierre ? "Operacion cerrada correctamente" : tramite ? "Pendiente de cierre" : "Primero debe subirse el tramite")
                .acciones(accionesCierre)
                .accion(accionesCierre.isEmpty() ? null : accionesCierre.get(0).getTipo())
                .accionLabel(accionesCierre.isEmpty() ? null : accionesCierre.get(0).getLabel())
                .completado(cierre)
                .bloqueado(!cierre && !tramite)
                .build());
        return hitos;
    }

    private boolean tieneRequisitosOperacionPendientes(
            List<RequisitoDocumentalExpediente> requisitos,
            TipoOperacionExpediente tipoOperacion
    ) {
        return requisitos.stream()
                .filter(requisito -> requisito.getEstado() != EstadoRequisitoDocumental.POSTERIOR)
                .filter(requisito -> requisito.getOperacion() != null && requisito.getOperacion().getTipo() == tipoOperacion)
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO);
    }

    private String calcularNotaDocumentacion(EstadoDetalle estadoDetalle) {
        if (estadoDetalle.finalizado()) {
            return "Expediente finalizado";
        }
        if (estadoDetalle.documentacionBaseCompleta()) {
            return "Documentacion base completa";
        }
        if (estadoDetalle.expedienteCompletoSubido()) {
            return "Hay un expediente completo subido; revisa y clasifica la documentacion base antes de avanzar";
        }
        return "Faltan documentos base requeridos";
    }

    private boolean requiereModelo620(Expediente expediente) {
        return tipoTramitePolicyService.requiereModelo620(expediente);
    }

    private boolean esDocumentoFinal(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.MODELO_620
                || tipoDocumento == TipoDocumento.COMPROBANTE_DGT
                || tipoDocumento == TipoDocumento.HUELLA_TRAMITE;
    }

    private boolean requisitoModeloResuelto(List<RequisitoDocumentalExpediente> requisitos, TipoOperacionExpediente tipoOperacion) {
        return requisitos.stream()
                .filter(requisito -> requisito.getTipoDocumento() == TipoDocumento.MODELO_620)
                .filter(requisito -> tipoOperacion == null
                        || (requisito.getOperacion() != null && requisito.getOperacion().getTipo() == tipoOperacion))
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.APORTADO
                        || requisito.getEstado() == EstadoRequisitoDocumental.OMITIDO);
    }

    private List<HitoAccionResponse> accionesHitoOperacion(boolean completado, boolean puedeAvanzar, CodigoHitoExpediente codigo, String avanceLabel, CodigoHitoExpediente retrocesoCodigo) {
        if (completado) {
            return List.of();
        }
        if (!puedeAvanzar) {
            return List.of();
        }
        return accionesConRetroceso(avanceLabel, codigo, retrocesoCodigo);
    }

    private List<HitoAccionResponse> accionesConRetroceso(String avanceLabel, CodigoHitoExpediente avanceCodigo, CodigoHitoExpediente retrocesoCodigo) {
        List<HitoAccionResponse> acciones = new ArrayList<>();
        if (avanceLabel != null && avanceCodigo != null) {
            acciones.add(HitoAccionResponse.builder()
                    .tipo("COMPLETAR_HITO")
                    .codigoHito(avanceCodigo.name())
                    .label(avanceLabel)
                    .tono("primary")
                    .build());
        }
        if (retrocesoCodigo != null) {
            acciones.add(accionRetroceso(retrocesoCodigo, "Retroceder"));
        }
        return acciones;
    }

    private HitoAccionResponse accionRetroceso(CodigoHitoExpediente codigo, String label) {
        return HitoAccionResponse.builder()
                .tipo("RETROCEDER_HITO")
                .codigoHito(codigo.name())
                .label(label)
                .tono("warning")
                .build();
    }

    private HitoAccionResponse accionRetrocesoFinalizacion() {
        return HitoAccionResponse.builder()
                .tipo("RETROCEDER_FINALIZACION")
                .label("Retroceder")
                .tono("warning")
                .build();
    }

    private List<HitoAccionResponse> accionesCierre(boolean finalizado, boolean conIncidencia, boolean puedeContinuar, boolean enviadoDgt) {
        if (finalizado || conIncidencia || !puedeContinuar) {
            return List.of();
        }
        List<HitoAccionResponse> acciones = new ArrayList<>();
        if (!enviadoDgt) {
            acciones.add(HitoAccionResponse.builder()
                    .tipo("COMPLETAR_HITO")
                    .label("Enviar a DGT")
                    .codigoHito(CodigoHitoExpediente.ENVIADO_DGT.name())
                    .tono("primary")
                    .build());
        }
        acciones.add(HitoAccionResponse.builder()
                .tipo("ABRIR_INCIDENCIA")
                .label("Incidencia")
                    .tono("warning")
                    .build());
        acciones.add(HitoAccionResponse.builder()
                .tipo("FINALIZAR")
                .label("Finalizado")
                .tono("success")
                .build());
        return acciones;
    }

    private String tituloCierre(boolean finalizado, boolean conIncidencia, boolean enviadoDgt) {
        if (finalizado) {
            return "Expediente finalizado";
        }
        if (conIncidencia) {
            return "Expediente con incidencia";
        }
        return enviadoDgt ? "Enviado a DGT" : "Cierre del expediente";
    }

    private String notaCierre(boolean finalizado, boolean conIncidencia, boolean enviadoDgt, boolean puedeContinuar) {
        if (finalizado) {
            return "Expediente cerrado correctamente";
        }
        if (conIncidencia) {
            return "Hay una incidencia abierta antes de continuar";
        }
        if (!puedeContinuar) {
            return "Primero debe subirse el tramite al programa de gestion";
        }
        return enviadoDgt ? "Pendiente de finalizar o abrir incidencia" : "Elige enviar a DGT, abrir incidencia o finalizar";
    }

    private HitoExpedienteResponse calcularSiguientePaso(EstadoDetalle estadoDetalle, List<HitoExpedienteResponse> hitos) {
        if (estadoDetalle.cancelado()) {
            return HitoExpedienteResponse.builder()
                    .id("expediente-cancelado")
                    .titulo("Expediente cancelado")
                    .descripcion("El cliente cancelo el tramite y no se realizara.")
                    .estado("COMPLETADO")
                    .tipo("ESTADO")
                    .nota("No hay acciones operativas pendientes.")
                    .completado(true)
                    .bloqueado(false)
                    .build();
        }
        if (estadoDetalle.finalizado()) {
            return HitoExpedienteResponse.builder()
                    .id("expediente-finalizado")
                    .titulo("Expediente finalizado")
                    .descripcion("El expediente ya esta cerrado.")
                    .estado("COMPLETADO")
                    .tipo("ESTADO")
                    .nota("No hay acciones pendientes detectadas.")
                    .completado(true)
                    .bloqueado(false)
                    .build();
        }
        if (estadoDetalle.conIncidencia()) {
            return HitoExpedienteResponse.builder()
                    .id("resolver-incidencia")
                    .titulo("Resolver incidencia")
                    .descripcion("Revisa la incidencia abierta y solicita o aporta la subsanacion necesaria.")
                    .estado("ACTUAL")
                    .tipo("INCIDENCIA")
                    .nota("Hay incidencias pendientes antes de continuar.")
                    .completado(false)
                    .bloqueado(false)
                    .build();
        }
        if (estadoDetalle.informacionSolicitada()) {
            return HitoExpedienteResponse.builder()
                    .id("informacion-adicional-solicitada")
                    .titulo("Esperando respuesta del cliente")
                    .descripcion("Se ha solicitado informacion adicional al cliente.")
                    .estado("ACTUAL")
                    .tipo("INFORMACION_ADICIONAL")
                    .nota("El cliente puede responder en la plataforma o el administrador resolver la solicitud directamente.")
                    .accion("RESOLVER_INFORMACION_ADICIONAL")
                    .accionLabel("Resolver solicitud")
                    .completado(false)
                    .bloqueado(false)
                    .build();
        }
        if (estadoDetalle.informacionRecibida()) {
            return HitoExpedienteResponse.builder()
                    .id("informacion-adicional-recibida")
                    .titulo("Revisar informacion recibida")
                    .descripcion("El cliente ya ha respondido a la informacion solicitada.")
                    .estado("ACTUAL")
                    .tipo("INFORMACION_ADICIONAL")
                    .nota("Revisa la respuesta y marca la informacion como revisada para continuar.")
                    .accion("RESOLVER_INFORMACION_ADICIONAL")
                    .accionLabel("Marcar revisada")
                    .completado(false)
                    .bloqueado(false)
                    .build();
        }
        if (estadoDetalle.documentacionSolicitada()) {
            return HitoExpedienteResponse.builder()
                    .id("documentacion-solicitada")
                    .titulo("Esperando documentacion del cliente")
                    .descripcion("El expediente queda pausado hasta completar los requisitos documentales solicitados.")
                    .estado("ACTUAL")
                    .tipo("DOCUMENTACION")
                    .nota("El cliente puede aportar cada documento desde su expediente.")
                    .completado(false)
                    .bloqueado(false)
                    .build();
        }
        if (estadoDetalle.requisitosInicialesPendientes()) {
            return HitoExpedienteResponse.builder()
                    .id("completar-checklist-documental")
                    .titulo("Completar checklist documental")
                    .descripcion("Revisa los requisitos documentales pendientes antes de avanzar.")
                    .estado("ACTUAL")
                    .tipo("DOCUMENTACION")
                    .nota("Hay requisitos documentales requeridos pendientes.")
                    .completado(false)
                    .bloqueado(false)
                    .build();
        }
        return hitos.stream()
                .filter(hito -> hito.getAccion() != null && !hito.isCompletado() && !hito.isBloqueado())
                .findFirst()
                .orElseGet(() -> hitos.stream()
                .filter(hito -> !hito.isCompletado() && !hito.isBloqueado())
                .findFirst()
                .orElseGet(() -> hitos.stream()
                        .filter(hito -> !hito.isCompletado())
                        .findFirst()
                        .orElse(null)));
    }

    private List<DocumentoExpedienteResponse> mapDocumentos(List<Documento> documentos, EstadoDetalle estadoDetalle) {
        List<DocumentoExpedienteResponse> resultado = new ArrayList<>();
        List<Long> documentoIds = documentos.stream()
                .map(Documento::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, DocumentoIdentidadLectura> lecturasPorDocumento = documentoIds.isEmpty()
                ? Map.of()
                : documentoIdentidadLecturaRepository.findByDocumentoIdIn(documentoIds)
                .stream()
                .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (actual, repetida) -> actual));
        Map<Long, DocumentoRolesLectura> lecturasRolesPorDocumento = documentoIds.isEmpty()
                ? Map.of()
                : documentoRolesLecturaRepository.findByDocumentoIdIn(documentoIds)
                .stream()
                .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (actual, repetida) -> actual));
        Map<Long, DocumentoVehiculoLectura> lecturasVehiculoPorDocumento = documentoIds.isEmpty()
                ? Map.of()
                : documentoVehiculoLecturaRepository.findByDocumentoIdIn(documentoIds)
                .stream()
                .filter(lectura -> lectura.getDocumento() != null && lectura.getDocumento().getId() != null)
                .collect(Collectors.toMap(lectura -> lectura.getDocumento().getId(), lectura -> lectura, (actual, repetida) -> actual));

        documentos.stream()
                .sorted(Comparator.comparing(Documento::getFechaSubida, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(documento -> mapDocumentoSubido(
                        documento,
                        lecturasPorDocumento.get(documento.getId()),
                        lecturasRolesPorDocumento.get(documento.getId()),
                        lecturasVehiculoPorDocumento.get(documento.getId())))
                .forEach(resultado::add);

        return resultado;
    }

    private DocumentoExpedienteResponse mapDocumentoSubido(Documento documento) {
        return mapDocumentoSubido(documento, null, null, null);
    }

    private DocumentoExpedienteResponse mapDocumentoSubido(
            Documento documento,
            DocumentoIdentidadLectura lecturaIdentidad,
            DocumentoRolesLectura lecturaRoles,
            DocumentoVehiculoLectura lecturaVehiculo
    ) {
        return DocumentoExpedienteResponse.builder()
                .id(documento.getId())
                .nombre(documento.getNombreArchivo())
                .nombreOriginal(documento.getNombreArchivoOriginal())
                .tipo(documento.getTipoDocumento() != null ? documento.getTipoDocumento().name() : null)
                .descripcion(documento.getDescripcionArchivo())
                .fechaSubida(formatearFecha(documento.getFechaSubida()))
                .subidoPor(documento.getSubidoPor() != null ? nombreCompleto(documento.getSubidoPor()) : null)
                .interesadoId(documento.getInteresado() != null ? documento.getInteresado().getId() : null)
                .interesadoNombre(documento.getInteresado() != null ? documento.getInteresado().getNombre() : null)
                .operacionId(documento.getOperacion() != null ? documento.getOperacion().getId() : null)
                .operacionLabel(documento.getOperacion() != null && documento.getOperacion().getTipo() != null
                        ? documento.getOperacion().getTipo().getLabel()
                        : null)
                .estado("SUBIDO")
                .subido(true)
                .requeridoAhora(false)
                .lecturaIdentidad(com.example.gestor_documental.dto.expediente.DocumentoIdentidadLecturaResponse.from(lecturaIdentidad))
                .lecturaRoles(com.example.gestor_documental.dto.expediente.DocumentoRolesLecturaResponse.from(lecturaRoles))
                .lecturaVehiculo(com.example.gestor_documental.dto.expediente.DocumentoVehiculoLecturaResponse.from(lecturaVehiculo))
                .build();
    }

    private RequisitoDocumentalResponse mapRequisitoDocumental(RequisitoDocumentalExpediente requisito) {
        return RequisitoDocumentalResponse.builder()
                .id(requisito.getId())
                .tipoDocumento(requisito.getTipoDocumento() != null ? requisito.getTipoDocumento().name() : null)
                .descripcion(requisito.getDescripcion())
                .estado(requisito.getEstado() != null ? requisito.getEstado().name() : null)
                .origen(requisito.getOrigen() != null ? requisito.getOrigen().name() : null)
                .interesadoId(requisito.getInteresado() != null ? requisito.getInteresado().getId() : null)
                .interesadoNombre(requisito.getInteresado() != null ? requisito.getInteresado().getNombre() : null)
                .rolInteresado(requisito.getRolInteresado() != null ? requisito.getRolInteresado().name() : null)
                .interesadoRepresentadoId(requisito.getInteresadoRepresentado() != null ? requisito.getInteresadoRepresentado().getId() : null)
                .interesadoRepresentadoNombre(requisito.getInteresadoRepresentado() != null ? requisito.getInteresadoRepresentado().getNombre() : null)
                .rolRepresentado(requisito.getRolRepresentado() != null ? requisito.getRolRepresentado().name() : null)
                .operacionId(requisito.getOperacion() != null ? requisito.getOperacion().getId() : null)
                .operacionLabel(requisito.getOperacion() != null && requisito.getOperacion().getTipo() != null
                        ? requisito.getOperacion().getTipo().getLabel()
                        : null)
                .documentoId(requisito.getDocumento() != null ? requisito.getDocumento().getId() : null)
                .documentoNombre(requisito.getDocumento() != null ? requisito.getDocumento().getNombreArchivoOriginal() : null)
                .motivoOmision(requisito.getMotivoOmision())
                .fechaCreacion(formatearFecha(requisito.getFechaCreacion()))
                .fechaResolucion(formatearFecha(requisito.getFechaResolucion()))
                .build();
    }

    private InteresadoExpedienteResponse mapInteresado(ExpedienteInteresado relacion) {
        Interesado interesado = relacion.getInteresado();
        return InteresadoExpedienteResponse.builder()
                .id(interesado != null ? interesado.getId() : null)
                .nombre(interesado != null ? interesado.getNombre() : null)
                .nombrePila(interesado != null ? interesado.getNombrePila() : null)
                .apellido1(interesado != null ? interesado.getApellido1() : null)
                .apellido2(interesado != null ? interesado.getApellido2() : null)
                .razonSocial(interesado != null ? interesado.getRazonSocial() : null)
                .rol(relacion.getRol() != null ? relacion.getRol().name() : null)
                .dni(interesado != null ? interesado.getDni() : null)
                .telefono(interesado != null ? interesado.getTelefono() : null)
                .direccion(interesado != null ? interesado.getDireccion() : null)
                .tipoVia(interesado != null ? interesado.getTipoVia() : null)
                .nombreVia(interesado != null ? interesado.getNombreVia() : null)
                .numeroVia(interesado != null ? interesado.getNumeroVia() : null)
                .bloque(interesado != null ? interesado.getBloque() : null)
                .portal(interesado != null ? interesado.getPortal() : null)
                .escalera(interesado != null ? interesado.getEscalera() : null)
                .piso(interesado != null ? interesado.getPiso() : null)
                .puerta(interesado != null ? interesado.getPuerta() : null)
                .codigoPostal(interesado != null ? interesado.getCodigoPostal() : null)
                .municipio(interesado != null ? interesado.getMunicipio() : null)
                .provincia(interesado != null ? interesado.getProvincia() : null)
                .build();
    }

    private IncidenciaExpedienteResponse mapIncidencia(Incidencia incidencia, List<Documento> documentos, Expediente expediente) {
        return IncidenciaExpedienteResponse.builder()
                .id(incidencia.getId())
                .tipo(incidencia.getTipoIncidencia() != null && incidencia.getTipoIncidencia().getNombre() != null
                        ? incidencia.getTipoIncidencia().getNombre().name()
                        : null)
                .observaciones(incidencia.getObservaciones())
                .fechaCreacion(formatearFecha(incidencia.getFechaCreacion()))
                .resuelta(incidencia.isResuelta())
                .fechaResolucion(formatearFecha(incidencia.getFechaResolucion()))
                .creadoPor(incidencia.getCreadoPor() != null ? nombreCompleto(incidencia.getCreadoPor()) : null)
                .resueltoPor(incidencia.getResueltoPor() != null ? nombreCompleto(incidencia.getResueltoPor()) : null)
                .contadorAvisos(incidencia.getContadorAvisos())
                .fechaUltimoAviso(formatearFecha(incidencia.getFechaUltimoAviso()))
                .pendienteRevisionCliente(!incidencia.isResuelta()
                        && expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS)
                .revisionComunicadaPorCliente(incidencia.isRevisionComunicadaPorCliente())
                .fechaRevisionComunicadaPorCliente(formatearFecha(incidencia.getFechaRevisionComunicadaPorCliente()))
                .comentarioRevisionCliente(incidencia.getComentarioRevisionCliente())
                .documentosRevision(documentos.stream()
                        .filter(documento -> documentoPerteneceARevisionIncidencia(documento, incidencia))
                        .sorted(Comparator.comparing(Documento::getFechaSubida, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(this::mapDocumentoSubido)
                        .toList())
                .build();
    }

    private boolean documentoPerteneceARevisionIncidencia(Documento documento, Incidencia incidencia) {
        if (documento.getIncidencia() != null && documento.getIncidencia().getId() != null) {
            return documento.getIncidencia().getId().equals(incidencia.getId());
        }
        return documento.getFechaSubida() != null
                && incidencia.getFechaCreacion() != null
                && documento.getFechaSubida().isAfter(incidencia.getFechaCreacion())
                && documento.getSubidoPor() != null
                && documento.getSubidoPor().getRolUsuario() == RolUsuario.CLIENTE;
    }

    private HistorialExpedienteResponse mapHistorial(HistorialCambio cambio) {
        return HistorialExpedienteResponse.builder()
                .id(cambio.getId())
                .accion(cambio.getAccion())
                .descripcion(cambio.getDescripcion())
                .fechaCambio(formatearFecha(cambio.getFechaCambio()))
                .usuario(cambio.getUsuario() != null ? nombreCompleto(cambio.getUsuario()) : null)
                .tipoActividad(cambio.getTipoActividad() != null ? cambio.getTipoActividad().name() : "CAMBIO")
                .build();
    }

    private MensajeExpedienteResponse mapMensaje(Mensaje mensaje, Usuario usuarioLogueado) {
        boolean noLeidoParaUsuario = false;
        if (usuarioLogueado != null && usuarioLogueado.getRolUsuario() == RolUsuario.ADMIN) {
            noLeidoParaUsuario = mensaje.getAutor() != null
                    && mensaje.getAutor().getRolUsuario() == RolUsuario.CLIENTE
                    && mensaje.getFechaLecturaAdmin() == null;
        } else if (usuarioLogueado != null) {
            noLeidoParaUsuario = mensaje.getAutor() != null
                    && mensaje.getAutor().getRolUsuario() == RolUsuario.ADMIN
                    && mensaje.getFechaLecturaCliente() == null;
        }
        return MensajeExpedienteResponse.builder()
                .id(mensaje.getId())
                .autor(mensaje.getAutor() != null ? nombreCompleto(mensaje.getAutor()) : null)
                .rolAutor(mensaje.getAutor() != null && mensaje.getAutor().getRolUsuario() != null
                        ? mensaje.getAutor().getRolUsuario().name()
                        : null)
                .fechaCreacion(formatearFecha(mensaje.getFechaCreacion()))
                .contenido(mensaje.getContenido())
                .noLeidoParaUsuario(noLeidoParaUsuario)
                .build();
    }

    private List<InconsistenciaDocumentalResponse> calcularInconsistenciasDocumentales(
            List<RequisitoDocumentalExpediente> requisitos,
            List<Documento> documentos,
            List<ExpedienteInteresado> interesados
    ) {
        List<InconsistenciaDocumentalResponse> resultado = new ArrayList<>();
        Map<RolInteresado, Interesado> interesadoActualPorRol = interesados.stream()
                .filter(relacion -> relacion.getRol() != null && relacion.getInteresado() != null)
                .collect(Collectors.toMap(
                        ExpedienteInteresado::getRol,
                        ExpedienteInteresado::getInteresado,
                        (actual, repetido) -> actual,
                        java.util.LinkedHashMap::new
                ));
        Map<String, RequisitoDocumentalExpediente> requeridoPorClave = new java.util.LinkedHashMap<>();

        for (RequisitoDocumentalExpediente requisito : requisitos) {
            if (requisito.getEstado() != EstadoRequisitoDocumental.REQUERIDO) {
                continue;
            }
            if (requisito.getDocumento() != null) {
                Documento documento = requisito.getDocumento();
                if (requisito.getInteresado() != null
                        && documento.getInteresado() != null
                        && !java.util.Objects.equals(requisito.getInteresado().getId(), documento.getInteresado().getId())) {
                    resultado.add(inconsistencia(
                            "DOCUMENTO_DE_OTRA_PERSONA",
                            "ALTA",
                            "Documento vinculado a otra persona",
                            "El requisito tiene un documento asociado, pero ese documento pertenece a otro interesado.",
                            requisito,
                            null,
                            "REVISAR_DOCUMENTO"
                    ));
                }
                continue;
            }

            String clave = claveRequisito(requisito);
            RequisitoDocumentalExpediente previo = requeridoPorClave.putIfAbsent(clave, requisito);
            if (previo != null) {
                resultado.add(inconsistencia(
                        "REQUISITO_DUPLICADO",
                        "MEDIA",
                        "Requisito duplicado",
                        "Hay mas de un requisito automatico pendiente para el mismo documento e interesado.",
                        requisito,
                        null,
                        "REVISAR_REQUISITO"
                ));
            }

            if (esDocumentoIdentidad(requisito.getTipoDocumento()) && requisito.getRolInteresado() != null) {
                Interesado interesadoActual = interesadoActualPorRol.get(requisito.getRolInteresado());
                if (interesadoActual != null && requisito.getInteresado() == null) {
                    resultado.add(inconsistencia(
                            "REQUISITO_SIN_INTERESADO",
                            "MEDIA",
                            "Requisito sin interesado",
                            "El requisito esta asociado al rol " + requisito.getRolInteresado().name() + ", pero no a la persona actual del expediente.",
                            requisito,
                            documentoSugerido(requisito, documentos, interesadoActual),
                            "VINCULAR_DOCUMENTO"
                    ));
                    continue;
                }
                if (interesadoActual != null
                        && requisito.getInteresado() != null
                        && !java.util.Objects.equals(requisito.getInteresado().getId(), interesadoActual.getId())) {
                    resultado.add(inconsistencia(
                            "REQUISITO_INTERESADO_DESACTUALIZADO",
                            "ALTA",
                            "Requisito apunta a otro interesado",
                            "El requisito no coincide con la persona actual del rol " + requisito.getRolInteresado().name() + ".",
                            requisito,
                            documentoSugerido(requisito, documentos, interesadoActual),
                            "VINCULAR_DOCUMENTO"
                    ));
                    continue;
                }
            }

            Documento sugerido = documentoSugerido(requisito, documentos, requisito.getInteresado());
            if (sugerido != null) {
                resultado.add(inconsistencia(
                        "DOCUMENTO_POSIBLE_SIN_VINCULAR",
                        "BAJA",
                        "Documento posible sin vincular",
                        "Existe un documento que podria cubrir este requisito, pero no esta vinculado automaticamente.",
                        requisito,
                        sugerido,
                        "VINCULAR_DOCUMENTO"
                ));
            }
        }
        return resultado.stream()
                .collect(Collectors.toMap(
                        item -> item.getCodigo() + "|" + item.getRequisitoId() + "|" + item.getDocumentoSugeridoId(),
                        item -> item,
                        (actual, repetido) -> actual,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private InconsistenciaDocumentalResponse inconsistencia(
            String codigo,
            String severidad,
            String titulo,
            String detalle,
            RequisitoDocumentalExpediente requisito,
            Documento documentoSugerido,
            String accionSugerida
    ) {
        return InconsistenciaDocumentalResponse.builder()
                .codigo(codigo)
                .severidad(severidad)
                .titulo(titulo)
                .detalle(detalle)
                .requisitoId(requisito.getId())
                .documentoSugeridoId(documentoSugerido != null ? documentoSugerido.getId() : null)
                .documentoSugeridoNombre(documentoSugerido != null ? documentoSugerido.getNombreArchivoOriginal() : null)
                .accionSugerida(accionSugerida)
                .build();
    }

    private Documento documentoSugerido(
            RequisitoDocumentalExpediente requisito,
            List<Documento> documentos,
            Interesado interesadoReferencia
    ) {
        return documentos.stream()
                .filter(documento -> documento.getId() != null)
                .filter(documento -> documentoCubreRequisitoLigero(documento, requisito))
                .filter(documento -> interesadoReferencia == null
                        || documento.getInteresado() == null
                        || java.util.Objects.equals(documento.getInteresado().getId(), interesadoReferencia.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean documentoCubreRequisitoLigero(Documento documento, RequisitoDocumentalExpediente requisito) {
        TipoDocumento documentoTipo = documento.getTipoDocumento();
        TipoDocumento requisitoTipo = requisito.getTipoDocumento();
        if (documentoTipo == null || requisitoTipo == null) {
            return false;
        }
        if (documentoTipo == requisitoTipo) {
            return true;
        }
        if (requisitoTipo == TipoDocumento.CONTRATO_COMPRAVENTA) {
            return documentoTipo == TipoDocumento.FACTURA;
        }
        if (requisitoTipo == TipoDocumento.PERMISO_CIRCULACION || requisitoTipo == TipoDocumento.FICHA_TECNICA) {
            return documentoTipo == TipoDocumento.INFORME_DGT;
        }
        return requisitoTipo == TipoDocumento.MANDATO && documentoTipo == TipoDocumento.MANDATO_REPRESENTACION;
    }

    private boolean esDocumentoIdentidad(TipoDocumento tipoDocumento) {
        return tipoDocumento == TipoDocumento.DNI || tipoDocumento == TipoDocumento.CIF;
    }

    private String claveRequisito(RequisitoDocumentalExpediente requisito) {
        return String.join("|",
                requisito.getTipoDocumento() != null ? requisito.getTipoDocumento().name() : "",
                requisito.getInteresado() != null && requisito.getInteresado().getId() != null ? requisito.getInteresado().getId().toString() : "",
                requisito.getRolInteresado() != null ? requisito.getRolInteresado().name() : "",
                requisito.getInteresadoRepresentado() != null && requisito.getInteresadoRepresentado().getId() != null ? requisito.getInteresadoRepresentado().getId().toString() : "",
                requisito.getRolRepresentado() != null ? requisito.getRolRepresentado().name() : "",
                requisito.getOperacion() != null && requisito.getOperacion().getId() != null ? requisito.getOperacion().getId().toString() : ""
        );
    }

    private ClienteResumenResponse mapCliente(Cliente cliente) {
        if (cliente == null) {
            return null;
        }
        return ClienteResumenResponse.builder()
                .id(cliente.getId())
                .nombre(cliente.getNombre())
                .nif(cliente.getNif())
                .email(cliente.getEmail())
                .telefono(cliente.getTelefono())
                .logoPrincipalUrl(ClienteBrandingUrls.logoUrl(cliente, TipoLogoCliente.PRINCIPAL))
                .logoCompactoUrl(ClienteBrandingUrls.logoUrl(cliente, TipoLogoCliente.COMPACTO))
                .build();
    }

    private ExpedienteVinculadoResponse mapTramiteVinculado(Expediente expediente) {
        Expediente origen = expediente.getExpedienteVinculadoOrigen();
        if (origen == null) {
            return null;
        }
        return ExpedienteVinculadoResponse.builder()
                .origenId(origen.getId())
                .origenReferencia("EXP-" + origen.getId())
                .origenEstado(origen.getEstadoExpediente() != null ? origen.getEstadoExpediente().name() : null)
                .motivoEspera(expediente.getMotivoEsperaVinculo())
                .esperandoFinalizacion(expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_TRAMITE_VINCULADO)
                .build();
    }

    private UsuarioResumenResponse mapUsuario(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return UsuarioResumenResponse.builder()
                .id(usuario.getId())
                .nombreCompleto(nombreCompleto(usuario))
                .email(usuario.getEmail())
                .rol(usuario.getRolUsuario() != null ? usuario.getRolUsuario().name() : null)
                .build();
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellidos = usuario.getApellidos() != null ? usuario.getApellidos() : "";
        String completo = (nombre + " " + apellidos).trim();
        return !completo.isBlank() ? completo : usuario.getEmail();
    }

    private String formatearFecha(LocalDateTime fecha) {
        return fecha != null ? fecha.toString() : null;
    }

    private String fechaHito(HitoExpediente hito, LocalDateTime fechaFallback) {
        if (hito != null) {
            return formatearFecha(hito.getFechaCompletado());
        }
        return formatearFecha(fechaFallback);
    }

    private String usuarioHito(HitoExpediente hito) {
        return hito != null && hito.getCompletadoPor() != null ? nombreCompleto(hito.getCompletadoPor()) : null;
    }

    private record EstadoDetalle(
            Set<TipoDocumento> tiposSubidos,
            boolean documentacionBaseCompleta,
            boolean expedienteCompletoSubido,
            boolean modelo620Subido,
            boolean requisitosInicialesPendientes,
            boolean finalizado,
            boolean cancelado,
            boolean conIncidencia,
            boolean documentacionSolicitada,
            boolean informacionSolicitada,
            boolean informacionRecibida,
            boolean tramiteSubido,
            boolean enviadoDgt,
            Map<CodigoHitoExpediente, HitoExpediente> hitosPersistidos,
            List<RequisitoDocumentalExpediente> requisitos
    ) {
    }
}
