package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Incidencia;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.RequisitoDocumentalExpediente;
import com.example.gestor_documental.model.WhatsappWebhookEvento;
import com.example.gestor_documental.enums.CodigoHitoExpediente;
import com.example.gestor_documental.enums.EstadoExpediente;
import com.example.gestor_documental.enums.EstadoRequisitoDocumental;
import com.example.gestor_documental.enums.EstadoSolicitud;
import com.example.gestor_documental.enums.EstadoWhatsappEvento;
import com.example.gestor_documental.model.Solicitud;
import com.example.gestor_documental.model.TipoTramite;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.repository.ExpedienteRepository;
import com.example.gestor_documental.repository.HitoExpedienteRepository;
import com.example.gestor_documental.repository.IncidenciaRepository;
import com.example.gestor_documental.repository.RequisitoDocumentalExpedienteRepository;
import com.example.gestor_documental.repository.SolicitudRepository;
import com.example.gestor_documental.repository.TipoTramiteRepository;
import com.example.gestor_documental.repository.WhatsappWebhookEventoRepository;
import com.example.gestor_documental.service.HistorialCambioService;
import com.example.gestor_documental.service.WhatsappMediaService;
import com.example.gestor_documental.service.WhatsappOutboundService;
import com.example.gestor_documental.service.WhatsappWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WhatsappWebhookServiceImpl implements WhatsappWebhookService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhatsappWebhookEventoRepository eventoRepository;
    private final ClienteRepository clienteRepository;
    private final ExpedienteRepository expedienteRepository;
    private final HitoExpedienteRepository hitoExpedienteRepository;
    private final IncidenciaRepository incidenciaRepository;
    private final RequisitoDocumentalExpedienteRepository requisitoRepository;
    private final SolicitudRepository solicitudRepository;
    private final TipoTramiteRepository tipoTramiteRepository;
    private final HistorialCambioService historialCambioService;
    private final WhatsappOutboundService whatsappOutboundService;
    private final WhatsappMediaService whatsappMediaService;

    private static final DateTimeFormatter FECHA_ESTADO_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${app.whatsapp.webhook.verify-token:}")
    private String verifyToken;

    @Value("${app.whatsapp.app-secret:}")
    private String appSecret;

    @Value("${app.public-url:}")
    private String publicUrl;

    @Override
    @Transactional
    public void procesar(String payload, String signature) {
        if (!firmaValida(payload, signature)) {
            throw new IllegalArgumentException("Firma de WhatsApp no valida.");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode value = root.path("entry").path(0).path("changes").path(0).path("value");
            JsonNode message = value.path("messages").path(0);
            if (message.isMissingNode() || message.isNull()) {
                return;
            }
            String messageId = text(message.path("id"));
            if (StringUtils.hasText(messageId) && eventoRepository.existsByMessageId(messageId)) {
                return;
            }

            WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
            evento.setPayload(payload);
            evento.setMessageId(messageId);
            evento.setTelefono(normalizarTelefono(text(message.path("from"))));
            evento.setTipo(text(message.path("type")));
            evento.setTexto(extraerTexto(message));
            evento.setAccionCodigo(extraerAccionCodigo(message));
            evento.setNombrePerfil(text(value.path("contacts").path(0).path("profile").path("name")));
            asociarClienteYExpediente(evento);
            boolean media = esMedia(message);
            if (media) {
                evento.setEstado(EstadoWhatsappEvento.REVISADO);
                evento.setFechaRevision(LocalDateTime.now());
            } else if (!procesarAccion(evento) && !procesarRespuestaEstadoTramite(evento) && !procesarRespuestaNuevaSolicitud(evento)) {
                procesarMenuEspontaneo(evento);
            }
            evento.setProcesado(true);
            evento = eventoRepository.save(evento);
            if (media) {
                whatsappMediaService.descargarYGuardar(evento, message);
                enviarMenuTrasDocumento(evento);
            }
        } catch (Exception ex) {
            WhatsappWebhookEvento evento = new WhatsappWebhookEvento();
            evento.setPayload(payload != null ? payload : "");
            evento.setProcesado(false);
            evento.setErrorProcesado(ex.getMessage());
            eventoRepository.save(evento);
            throw new IllegalArgumentException("No se pudo procesar el webhook de WhatsApp.", ex);
        }
    }

    @Override
    public boolean verificarToken(String token) {
        return StringUtils.hasText(verifyToken) && verifyToken.equals(token);
    }

    @Override
    public boolean firmaValida(String payload, String signature) {
        if (!StringUtils.hasText(appSecret)) {
            return true;
        }
        if (!StringUtils.hasText(signature) || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    private void asociarClienteYExpediente(WhatsappWebhookEvento evento) {
        if (!StringUtils.hasText(evento.getTelefono())) {
            return;
        }
        Optional<Cliente> cliente = clienteRepository.findAll().stream()
                .filter(item -> coincideTelefono(evento.getTelefono(), item.getTelefono()))
                .findFirst();
        cliente.ifPresent(evento::setCliente);
        cliente.ifPresent(item -> {
            List<Expediente> expedientes = expedienteRepository.findByClienteIdOrderByFechaReferenciaDesc(item.getId());
            if (!expedientes.isEmpty()) {
                evento.setExpediente(expedientes.get(0));
            }
        });
    }

    private boolean coincideTelefono(String origen, String telefonoCliente) {
        String cliente = normalizarTelefono(telefonoCliente);
        if (!StringUtils.hasText(cliente)) {
            return false;
        }
        return origen.endsWith(cliente) || cliente.endsWith(origen);
    }

    private boolean procesarAccion(WhatsappWebhookEvento evento) {
        if (!StringUtils.hasText(evento.getAccionCodigo())) {
            return false;
        }
        String accion = evento.getAccionCodigo();
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            return true;
        }
        if ("gestapp_estado_tramite".equals(accion) || "gestapp_menu_expedientes".equals(accion)) {
            solicitarMatriculaEstadoTramite(evento);
            return true;
        }
        if ("gestapp_menu_pendiente".equals(accion)) {
            enviarDocumentacionPendiente(evento);
            return true;
        }
        if ("gestapp_solicitud_aportar_documentos".equals(accion)) {
            enviarEnlaceUltimaSolicitud(evento, "Puedes aportar documentacion desde la solicitud:");
            return true;
        }
        if ("gestapp_solicitud_estado".equals(accion)) {
            enviarEstadoUltimaSolicitud(evento);
            return true;
        }
        if ("gestapp_nueva_solicitud".equals(accion)) {
            iniciarNuevaSolicitud(evento);
            return true;
        }
        if (accion.startsWith("gestapp_solicitud_tipo_")) {
            solicitarMatriculaNuevaSolicitud(evento);
            return true;
        }
        if ("gestapp_menu_principal".equals(accion)) {
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_salir".equals(accion)) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "Perfecto, lo dejamos aqui. Cuando necesites cualquier cosa, escribe menu y seguimos.");
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_cambiar_expediente".equals(accion) || "gestapp_consultar_otro".equals(accion)) {
            solicitarMatriculaEstadoTramite(evento);
            return true;
        }
        if ("gestapp_contactar_general".equals(accion)) {
            confirmarContactoGeneral(evento);
            return true;
        }
        if ("gestapp_contactar_solicitud".equals(accion)) {
            confirmarContactoSolicitud(evento);
            return true;
        }
        if ("gestapp_contactar".equals(accion)) {
            confirmarContactoExpediente(evento);
            return true;
        }
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes continuar desde el portal de GestApp:");
            return true;
        }
        if ("gestapp_estado_actual".equals(accion)) {
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), mensajeEstadoTramite(evento.getExpediente()));
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_subir_mas_documentacion".equals(accion)) {
            enviarEnlacePortalSinHistorial(evento, "Puedes subir la documentacion desde el portal:");
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo de nuevo las opciones del expediente.");
            marcarRevisado(evento);
            return true;
        }
        if ("gestapp_ver_pendientes".equals(accion)) {
            enviarDocumentacionPendiente(evento);
            return true;
        }
        if ("gestapp_recordar_manana".equals(accion)) {
            posponerSeguimiento(evento);
            return true;
        }
        if ("gestapp_enviar_documentacion".equals(accion)) {
            enviarEnlacePortal(evento, "Puedes subir la documentacion desde el portal:");
            return true;
        }
        if ("gestapp_ver_expediente".equals(accion)) {
            enviarEnlacePortal(evento, "Puedes consultar el expediente desde el portal:");
            return true;
        }
        return false;
    }

    private void solicitarMatriculaEstadoTramite(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto. Indícame la matrícula del vehículo para consultar el estado del trámite.");
        marcarRevisado(evento);
    }

    private boolean procesarRespuestaEstadoTramite(WhatsappWebhookEvento evento) {
        if (!"text".equals(evento.getTipo()) || evento.getCliente() == null || !StringUtils.hasText(evento.getTexto())) {
            return false;
        }
        Optional<WhatsappWebhookEvento> anterior = eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono());
        if (anterior.isEmpty() || !esAccionEstadoTramite(anterior.get().getAccionCodigo())) {
            return false;
        }
        String matricula = normalizarMatricula(evento.getTexto());
        if (!StringUtils.hasText(matricula) || matricula.length() < 4 || matricula.length() > 10) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No he podido reconocer la matrícula. Envíamela sin espacios ni guiones, por ejemplo 1234ABC.");
            marcarRevisado(evento);
            return true;
        }
        List<Expediente> expedientes = expedienteRepository.findByClienteIdAndMatriculaNormalizada(evento.getCliente().getId(), matricula);
        if (expedientes.isEmpty()) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No encuentro un trámite dado de alta para la matrícula " + matricula + " asociado a este teléfono. Revisa la matrícula o solicita contacto con la gestoría.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return true;
        }
        Expediente expediente = expedientes.get(0);
        evento.setExpediente(expediente);
        whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), expediente, mensajeEstadoTramite(expediente));
        marcarRevisado(evento);
        historialCambioService.registrarCambioExpediente(expediente, expediente.getModificadoPor(), "WHATSAPP ESTADO",
                "Se envio al cliente el estado del tramite por WhatsApp tras consultar la matricula " + matricula + ".");
        return true;
    }

    private void iniciarNuevaSolicitud(WhatsappWebhookEvento evento) {
        List<TipoTramite> tipos = tipoTramiteRepository.findAll().stream()
                .filter(TipoTramite::isActivo)
                .toList();
        whatsappOutboundService.enviarMenuTiposSolicitud(evento.getTelefono(), tipos);
        marcarRevisado(evento);
    }

    private void solicitarMatriculaNuevaSolicitud(WhatsappWebhookEvento evento) {
        String tipo = tipoSeleccionado(evento.getAccionCodigo())
                .flatMap(tipoTramiteRepository::findById)
                .map(this::descripcionTipoTramite)
                .orElse("el tramite seleccionado");
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto. Ahora dime la matricula del vehiculo para crear la solicitud de " + tipo + ".");
        marcarRevisado(evento);
    }

    private boolean procesarRespuestaNuevaSolicitud(WhatsappWebhookEvento evento) {
        if (!"text".equals(evento.getTipo()) || evento.getCliente() == null || !StringUtils.hasText(evento.getTexto())) {
            return false;
        }
        Optional<WhatsappWebhookEvento> anterior = eventoRepository.findTopByTelefonoAndMessageIdIsNotNullOrderByFechaRecepcionDesc(evento.getTelefono());
        if (anterior.isEmpty() || !StringUtils.hasText(anterior.get().getAccionCodigo())
                || !anterior.get().getAccionCodigo().startsWith("gestapp_solicitud_tipo_")) {
            return false;
        }
        Optional<Long> tipoId = tipoSeleccionado(anterior.get().getAccionCodigo());
        if (tipoId.isEmpty()) {
            return false;
        }
        TipoTramite tipoTramite = tipoTramiteRepository.findById(tipoId.get()).orElse(null);
        if (tipoTramite == null || !tipoTramite.isActivo()) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(), "No he podido encontrar ese tipo de tramite. Escribe menu y vuelve a intentarlo.");
            marcarRevisado(evento);
            return true;
        }
        String matricula = normalizarMatricula(evento.getTexto());
        if (!StringUtils.hasText(matricula) || matricula.length() < 4 || matricula.length() > 10) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(),
                    "No he podido reconocer la matricula. Enviamela sin espacios ni guiones, por ejemplo 1234ABC.");
            marcarRevisado(evento);
            return true;
        }
        Solicitud solicitud = new Solicitud();
        solicitud.setCliente(evento.getCliente());
        solicitud.setTipoTramite(tipoTramite);
        solicitud.setMatricula(matricula);
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE_REVISION);
        solicitud.setObservaciones("Solicitud creada desde WhatsApp.");
        solicitud = solicitudRepository.save(solicitud);
        historialCambioService.registrarCambioSolicitud(solicitud, null, "WHATSAPP SOLICITUD",
                "Solicitud creada desde WhatsApp para la matricula " + matricula + ".");
        whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud,
                "Solicitud SOL-" + solicitud.getId() + " creada con exito.");
        marcarRevisado(evento);
        return true;
    }

    private Optional<Long> tipoSeleccionado(String accionCodigo) {
        if (!StringUtils.hasText(accionCodigo) || !accionCodigo.startsWith("gestapp_solicitud_tipo_")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(accionCodigo.substring("gestapp_solicitud_tipo_".length())));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String descripcionTipoTramite(TipoTramite tipoTramite) {
        if (tipoTramite == null) {
            return "tramite";
        }
        if (StringUtils.hasText(tipoTramite.getDescripcion())) {
            return tipoTramite.getDescripcion();
        }
        return tipoTramite.getNombre() != null ? tipoTramite.getNombre().name().replace('_', ' ') : "tramite";
    }

    private boolean esAccionEstadoTramite(String accionCodigo) {
        return "gestapp_estado_tramite".equals(accionCodigo)
                || "gestapp_menu_expedientes".equals(accionCodigo)
                || "gestapp_cambiar_expediente".equals(accionCodigo)
                || "gestapp_consultar_otro".equals(accionCodigo);
    }

    private String mensajeEstadoTramite(Expediente expediente) {
        String matricula = StringUtils.hasText(expediente.getMatricula()) ? normalizarMatricula(expediente.getMatricula()) : "Sin matricula";
        String tramite = expediente.getTipoTramite() != null && StringUtils.hasText(expediente.getTipoTramite().getDescripcion())
                ? expediente.getTipoTramite().getDescripcion()
                : "Tramite";
        String fecha = expediente.getFechaUltimaModificacion() != null
                ? "\nUltima actualizacion: " + expediente.getFechaUltimaModificacion().format(FECHA_ESTADO_FORMATTER)
                : "";
        return "Estado del tramite\n"
                + "Matricula: " + matricula + "\n"
                + "Tramite: " + tramite + "\n"
                + "Fase actual: " + faseActualWhatsapp(expediente) + "\n"
                + "Siguiente paso: " + siguientePasoWhatsapp(expediente)
                + fecha;
    }

    private String faseActualWhatsapp(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            return "Finalizado";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.RECHAZADO) {
            return "Rechazado";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA
                || expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return "Incidencias";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            return "Pendiente de documentacion";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
            return "Solicitada informacion adicional";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.INFORMACION_ADICIONAL_RECIBIDA) {
            return "Informacion adicional recibida";
        }
        if (documentacionPendiente(expediente)) {
            return "Comprobacion de documentacion";
        }
        if (!hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION)) {
            return "Pendiente de subir a programa de gestion";
        }
        if (!modelo620Presentado(expediente)) {
            return "Tramite subido, a la espera de pasar el impuesto 620";
        }
        if (expediente.getEstadoExpediente() != EstadoExpediente.ENVIADO_DGT
                && !hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.ENVIADO_DGT)) {
            return "Impuesto 620 presentado, pendiente de enviar a DGT";
        }
        return "Tramite enviado a DGT, pendiente de cierre";
    }

    private String siguientePasoWhatsapp(Expediente expediente) {
        if (expediente.getEstadoExpediente() == EstadoExpediente.FINALIZADO) {
            return "El expediente ya esta cerrado.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.RECHAZADO) {
            return "Contactar con la gestoria si necesitas mas informacion.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.PENDIENTE_DOCUMENTACION) {
            return "Aportar la documentacion pendiente.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.SOLICITADA_INFORMACION_ADICIONAL) {
            return "Responder a la informacion solicitada.";
        }
        if (expediente.getEstadoExpediente() == EstadoExpediente.INCIDENCIA
                || expediente.getEstadoExpediente() == EstadoExpediente.REVISANDO_INCIDENCIAS) {
            return "Resolver la incidencia abierta.";
        }
        if (documentacionPendiente(expediente)) {
            return "La gestoria esta comprobando la documentacion.";
        }
        if (!hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.TRAMITE_PROGRAMA_GESTION)) {
            return "Subir el tramite al programa de gestion.";
        }
        if (!modelo620Presentado(expediente)) {
            return "Presentar el impuesto 620.";
        }
        if (expediente.getEstadoExpediente() != EstadoExpediente.ENVIADO_DGT
                && !hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.ENVIADO_DGT)) {
            return "Enviar el tramite a DGT.";
        }
        return "Finalizar el expediente cuando DGT lo confirme.";
    }

    private boolean documentacionPendiente(Expediente expediente) {
        return requisitoRepository.findByExpedienteIdOrderByIdAsc(expediente.getId()).stream()
                .anyMatch(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO);
    }

    private boolean modelo620Presentado(Expediente expediente) {
        return hitoExpedienteRepository.existsByExpedienteIdAndCodigo(expediente.getId(), CodigoHitoExpediente.MODELO_620_PRESENTADO);
    }

    private void procesarMenuEspontaneo(WhatsappWebhookEvento evento) {
        if (!esSolicitudMenu(evento.getTexto())) {
            return;
        }
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            return;
        }
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private void posponerSeguimiento(WhatsappWebhookEvento evento) {
        Optional<Incidencia> incidencia = incidenciaRepository.findByExpedienteIdAndResueltaFalse(evento.getExpediente().getId()).stream()
                .filter(item -> !item.isSeguimientoArchivado())
                .max(Comparator.comparing(item -> item.getFechaCreacion() != null ? item.getFechaCreacion() : LocalDateTime.MIN));
        if (incidencia.isEmpty()) {
            return;
        }
        LocalDateTime proximoAviso = LocalDateTime.now().plusDays(1).with(LocalTime.of(9, 0));
        incidencia.get().setProximoAviso(proximoAviso);
        incidenciaRepository.save(incidencia.get());
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        historialCambioService.registrarCambioExpediente(evento.getExpediente(), evento.getExpediente().getModificadoPor(), "WHATSAPP ACCION",
                "El cliente pidio recordatorio por WhatsApp. Seguimiento pospuesto hasta " + proximoAviso + ".");
    }

    private void enviarMenuTrasDocumento(WhatsappWebhookEvento evento) {
        if (evento == null || evento.getExpediente() == null || !StringUtils.hasText(evento.getTelefono())) {
            return;
        }
        whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(),
                "Documento recibido correctamente.");
    }

    private void confirmarContactoGeneral(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto, paso el aviso a la gestoria para que contacten contigo.");
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
    }

    private void confirmarContactoSolicitud(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto, paso el aviso a la gestoria para que revisen tu solicitud.");
        Solicitud solicitud = evento.getCliente() != null
                ? solicitudRepository.findByClienteIdOrderByFechaReferenciaDesc(evento.getCliente().getId()).stream().findFirst().orElse(null)
                : null;
        if (solicitud != null) {
            whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud, "Te dejo las opciones de la solicitud.");
        } else {
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        }
    }

    private void confirmarContactoExpediente(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Perfecto, paso el aviso a la gestoria para que contacten contigo.");
        if (evento.getExpediente() != null) {
            whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo las opciones del expediente.");
        } else {
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        }
    }

    private void enviarEnlacePortal(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/expedientes/" + evento.getExpediente().getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
        historialCambioService.registrarCambioExpediente(evento.getExpediente(), evento.getExpediente().getModificadoPor(), "WHATSAPP ACCION",
                "Se envio al cliente un enlace al portal tras pulsar un boton de WhatsApp.");
    }

    private void enviarEnlacePortalSinHistorial(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/expedientes/" + evento.getExpediente().getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
    }

    private void enviarEnlacePortalCliente(WhatsappWebhookEvento evento, String introduccion) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/cliente/expedientes";
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private void enviarEnlaceNuevaSolicitud(WhatsappWebhookEvento evento) {
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/cliente/solicitudes/nuevo";
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Puedes iniciar una nueva solicitud desde el portal:\n" + enlace);
        whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
        marcarRevisado(evento);
    }

    private void enviarEstadoUltimaSolicitud(WhatsappWebhookEvento evento) {
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            marcarRevisado(evento);
            return;
        }
        Solicitud solicitud = solicitudRepository.findByClienteIdOrderByFechaReferenciaDesc(evento.getCliente().getId()).stream()
                .findFirst()
                .orElse(null);
        if (solicitud == null) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(), "No veo solicitudes asociadas a este telefono.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return;
        }
        whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud,
                "Estado: " + estadoSolicitudTexto(solicitud) + ".");
        marcarRevisado(evento);
    }

    private void enviarEnlaceUltimaSolicitud(WhatsappWebhookEvento evento, String introduccion) {
        if (evento.getCliente() == null) {
            enviarIdentificacionNecesaria(evento);
            marcarRevisado(evento);
            return;
        }
        Solicitud solicitud = solicitudRepository.findByClienteIdOrderByFechaReferenciaDesc(evento.getCliente().getId()).stream()
                .findFirst()
                .orElse(null);
        if (solicitud == null) {
            whatsappOutboundService.enviarTexto(evento.getTelefono(), "No veo solicitudes asociadas a este telefono.");
            whatsappOutboundService.enviarMenuPrincipal(evento.getTelefono());
            marcarRevisado(evento);
            return;
        }
        String base = publicUrl != null ? publicUrl : "";
        String enlace = (StringUtils.hasText(base) ? base.replaceAll("/$", "") : "") + "/solicitudes/" + solicitud.getId();
        whatsappOutboundService.enviarTexto(evento.getTelefono(), introduccion + "\n" + enlace);
        whatsappOutboundService.enviarMenuContinuacionSolicitud(evento.getTelefono(), solicitud, "Te dejo las opciones de la solicitud.");
        marcarRevisado(evento);
    }

    private String estadoSolicitudTexto(Solicitud solicitud) {
        if (solicitud.getEstadoSolicitud() == null) {
            return "pendiente de revision";
        }
        return solicitud.getEstadoSolicitud().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void enviarDocumentacionPendiente(WhatsappWebhookEvento evento) {
        if (evento.getExpediente() == null) {
            enviarEnlacePortalCliente(evento, "Puedes revisar la documentacion pendiente desde el portal:");
            return;
        }
        List<String> pendientes = requisitoRepository.findByExpedienteIdOrderByIdAsc(evento.getExpediente().getId()).stream()
                .filter(requisito -> requisito.getEstado() == EstadoRequisitoDocumental.REQUERIDO)
                .map(this::descripcionRequisito)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
        String detalle = pendientes.isEmpty()
                ? "No veo documentacion marcada como pendiente en tu ultimo expediente. Puedes revisarlo en el portal:"
                : "Documentacion pendiente en tu ultimo expediente:\n- " + String.join("\n- ", pendientes) + "\n\nPuedes aportarla desde el portal:";
        enviarEnlacePortalSinHistorial(evento, detalle);
        whatsappOutboundService.enviarMenuContinuacion(evento.getTelefono(), evento.getExpediente(), "Te dejo las opciones del expediente.");
        marcarRevisado(evento);
    }

    private String descripcionRequisito(RequisitoDocumentalExpediente requisito) {
        if (StringUtils.hasText(requisito.getDescripcion())) {
            return requisito.getDescripcion().trim();
        }
        if (requisito.getTipoDocumento() != null) {
            return requisito.getTipoDocumento().name().replace('_', ' ');
        }
        return null;
    }

    private void enviarIdentificacionNecesaria(WhatsappWebhookEvento evento) {
        whatsappOutboundService.enviarTexto(evento.getTelefono(),
                "Hola, soy GestApp. No he podido identificar este telefono. Responde con tu nombre y la gestoria revisara la asociacion.");
    }

    private void marcarRevisado(WhatsappWebhookEvento evento) {
        evento.setEstado(EstadoWhatsappEvento.REVISADO);
        evento.setFechaRevision(LocalDateTime.now());
    }

    private boolean esSolicitudMenu(String texto) {
        String limpio = normalizarTexto(texto);
        if (!StringUtils.hasText(limpio)) {
            return false;
        }
        if (pareceMatricula(limpio)) {
            return false;
        }
        if (contieneIntencionMenu(limpio)) {
            return true;
        }
        return List.of("hola", "buenas", "buenos dias", "buenas tardes", "menu", "menú", "ayuda", "inicio", "gestapp")
                .contains(limpio);
    }

    private boolean contieneIntencionMenu(String texto) {
        return texto.contains("menu")
                || texto.contains("ayuda")
                || texto.contains("opciones")
                || texto.contains("empezar")
                || texto.contains("inicio")
                || texto.contains("volver")
                || texto.contains("gestapp")
                || texto.contains("hacer una consulta")
                || texto.contains("nueva consulta")
                || texto.contains("quiero consultar")
                || texto.contains("necesito consultar")
                || texto.contains("necesito ayuda")
                || texto.contains("abrir menu")
                || texto.contains("ver menu")
                || texto.contains("mostrar menu");
    }

    private boolean pareceMatricula(String texto) {
        String normalizada = normalizarMatricula(texto);
        return StringUtils.hasText(normalizada)
                && normalizada.length() >= 5
                && normalizada.length() <= 8
                && normalizada.matches(".*\\d.*")
                && normalizada.matches(".*[A-Z].*");
    }

    private String normalizarTexto(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }
        String sinAcentos = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizarMatricula(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sinAcentos = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String extraerTexto(JsonNode message) {
        String tipo = text(message.path("type"));
        if ("text".equals(tipo)) {
            return text(message.path("text").path("body"));
        }
        if ("button".equals(tipo)) {
            return text(message.path("button").path("text"));
        }
        if ("interactive".equals(tipo)) {
            String button = text(message.path("interactive").path("button_reply").path("title"));
            return StringUtils.hasText(button) ? button : text(message.path("interactive").path("list_reply").path("title"));
        }
        if ("image".equals(tipo)) {
            String caption = text(message.path("image").path("caption"));
            return StringUtils.hasText(caption) ? caption : "Imagen recibida por WhatsApp";
        }
        if ("document".equals(tipo)) {
            String caption = text(message.path("document").path("caption"));
            String filename = text(message.path("document").path("filename"));
            if (StringUtils.hasText(caption)) {
                return caption;
            }
            return StringUtils.hasText(filename) ? filename : "Documento recibido por WhatsApp";
        }
        return null;
    }

    private boolean esMedia(JsonNode message) {
        String tipo = text(message.path("type"));
        return "image".equals(tipo) || "document".equals(tipo);
    }

    private String extraerAccionCodigo(JsonNode message) {
        if (!"interactive".equals(text(message.path("type")))) {
            return null;
        }
        String button = text(message.path("interactive").path("button_reply").path("id"));
        return StringUtils.hasText(button) ? button : text(message.path("interactive").path("list_reply").path("id"));
    }

    private String text(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText(null) : null;
    }

    private String normalizarTelefono(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9]", "");
        if (normalized.startsWith("00")) normalized = normalized.substring(2);
        if (normalized.startsWith("34") && normalized.length() == 11) normalized = normalized.substring(2);
        return normalized;
    }
}
